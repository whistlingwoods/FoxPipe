package us.shandian.giga.postprocessing;

import android.util.Log;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.File;
import java.io.IOException;

import us.shandian.giga.io.FileStream;

/**
 * Composite post-processor that chains multiple post-processing algorithms.
 * Each algorithm is applied sequentially to the output of the previous one.
 *
 * <p>This is useful for operations like:
 * <ul>
 *   <li>M4A DASH format conversion followed by metadata tagging</li>
 *   <li>WebM to OGG demuxing followed by metadata tagging</li>
 * </ul>
 *
 * <p>Note: All chained algorithms must have worksOnSameFile=true.
 */
class CompositePostprocessing extends Postprocessing {
    private static final String TAG = "CompositePostprocessing";
    private static final int BUFFER_SIZE = 8192;

    private Postprocessing[] chain;

    /**
     * No-arg constructor for instantiation via Postprocessing.getAlgorithm().
     * The actual algorithms will be initialized from args later.
     */
    public CompositePostprocessing() {
        super(true, true, "composite");
    }

    /**
     * Creates a composite post-processor with the given algorithm chain.
     *
     * @param algorithms post-processing algorithms to chain (in order)
     */
    CompositePostprocessing(Postprocessing... algorithms) {
        super(
            checkReserveSpace(algorithms),
            checkWorksOnSameFile(algorithms),
            "composite"
        );
        this.chain = algorithms;

        if (algorithms.length == 0) {
            throw new IllegalArgumentException("At least one algorithm must be provided");
        }

        // Verify all algorithms work on the same file
        for (Postprocessing algorithm : algorithms) {
            if (!algorithm.worksOnSameFile) {
                throw new IllegalArgumentException(
                    "All algorithms in a composite must have worksOnSameFile=true, but got: "
                        + algorithm
                );
            }
        }
    }

    /**
     * Initializes the algorithm chain from args.
     * Args format: [algo1_name, algo2_name, ..., algoN_args...]
     * All algorithms share the remaining args.
     */
    private void initializeFromArgs() {
        if (args == null || args.length < 2) {
            throw new IllegalArgumentException(
                "Composite postprocessing requires at least 2 args: "
                    + "[algo1_name, algo2_name_or_first_arg, ...]");
        }

        // First arg is the format conversion algorithm name
        final String algo1Name = args[0];
        // Second arg is the metadata tagging algorithm name
        final String algo2Name = args[1];

        // Remaining args (from index 2 onwards) are for the metadata tagging algorithm
        final String[] metadataArgs = new String[args.length - 2];
        System.arraycopy(args, 2, metadataArgs, 0, args.length - 2);

        // Create the two algorithms
        final Postprocessing algo1 = Postprocessing.getAlgorithm(algo1Name, null, streamInfo);
        final Postprocessing algo2 = Postprocessing.getAlgorithm(algo2Name, metadataArgs, streamInfo);

        this.chain = new Postprocessing[]{algo1, algo2};
    }

    @Override
    boolean test(SharpStream... sources) throws IOException {
        // Initialize chain from args if not yet initialized
        if (chain == null) {
            initializeFromArgs();
        }

        // Test if any algorithm in the chain needs to run
        for (Postprocessing algorithm : chain) {
            if (algorithm.test(sources)) {
                return true;
            }
        }
        return false;
    }

    @Override
    int process(SharpStream out, SharpStream... sources) throws IOException {
        // Initialize chain from args if not yet initialized
        if (chain == null) {
            initializeFromArgs();
        }

        // For a chain of algorithms, we need to apply each one sequentially
        // The output of one becomes the input of the next

        if (chain.length == 1) {
            // Simple case: only one algorithm
            return chain[0].process(out, sources);
        }

        // Complex case: multiple algorithms
        // We need temporary files to store intermediate results
        File[] tempFiles = new File[chain.length - 1];
        SharpStream currentOutput = out;

        try {
            // Apply each algorithm in the chain
            for (int i = 0; i < chain.length; i++) {
                Postprocessing algorithm = chain[i];

                if (i < chain.length - 1) {
                    // Not the last algorithm - write to a temp file
                    tempFiles[i] = File.createTempFile("newpipe_composite_", ".tmp");
                    Log.d(TAG, "Applying algorithm " + (i + 1) + "/" + chain.length
                        + ": " + algorithm);

                    // Process: sources -> tempFile
                    // Use FileStream which supports seeking (needed for M4A conversion)
                    try (FileStream tempStream = new FileStream(tempFiles[i])) {
                        int result = algorithm.process(tempStream, sources);
                        if (result != OK_RESULT) {
                            Log.e(TAG, "Algorithm " + (i + 1) + " failed with result: "
                                + result);
                            return result;
                        }
                    }

                    // Close previous sources
                    for (SharpStream source : sources) {
                        if (source != null && !source.isClosed()) {
                            source.close();
                        }
                    }

                    // Prepare for next iteration: use temp file as source
                    sources = new SharpStream[]{new FileStream(tempFiles[i])};

                } else {
                    // Last algorithm - write to final output
                    Log.d(TAG, "Applying final algorithm " + (i + 1) + "/" + chain.length + ": " + algorithm);
                    int result = algorithm.process(out, sources);
                    if (result != OK_RESULT) {
                        Log.e(TAG, "Final algorithm failed with result: " + result);
                        return result;
                    }
                }
            }

            Log.d(TAG, "Successfully completed composite post-processing chain");
            return OK_RESULT;

        } finally {
            // Close any remaining open sources
            for (SharpStream source : sources) {
                if (source != null && !source.isClosed()) {
                    try {
                        source.close();
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to close source stream", e);
                    }
                }
            }

            // Clean up temp files
            for (File tempFile : tempFiles) {
                if (tempFile != null && tempFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
                }
            }
        }
    }

    /**
     * Checks if any algorithm in the chain requires reserved space.
     */
    private static boolean checkReserveSpace(Postprocessing[] algorithms) {
        for (Postprocessing algorithm : algorithms) {
            if (algorithm.reserveSpace) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if all algorithms work on the same file.
     */
    private static boolean checkWorksOnSameFile(Postprocessing[] algorithms) {
        // For composite to work, all algorithms must work on the same file
        for (Postprocessing algorithm : algorithms) {
            if (!algorithm.worksOnSameFile) {
                return false;
            }
        }
        return true;
    }
}
