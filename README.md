# NewPipe kids-mode 🧒

## Motivation

This repository is a fork —intended to be as close to the upstream as possible— of the original [NewPipe](https://github.com/TeamNewPipe/NewPipe). My aim when creating this fork was to protect my children from watching random content from YouTube. There is YouTube Kids of course, but it doesn't meet my needs. Actually it is not only me, as seen in [this](https://github.com/TeamNewPipe/NewPipe/issues/1950) issue and some others like [this](https://github.com/TeamNewPipe/NewPipe/issues/677). 

Since I'm neither a native Android developer nor have time to make an elegant GUI to whitelist some channels/playlists, I've come up with a _much simpler_ solution. I've just modified the search bar so that you need to enter certain password before the search term. Since —hopefully— my children don't know the password, they can't make searches themselves. I search for videos, playlists or channels and bookmark them. They effectively become whitelisted. There are also some settings I have changed the default values so that kids won't be directed to other content. These changes include removing default kiosk from the main page, disabling comments, suggested next/similar videos, video descriptions and channel about tab.

__So to sum up:__

- This fork of NewPipe —that I call NewPipe Kids Mode— has minimal and primitive changes but is functional enough so I use it for my children.
- It disables standard search functionality; indeed, it requires you to type the literal word `password` before any search term.
- There is no GUI to change the password so try to keep it really secret =)
- This can be installed independently from the original NewPipe.
- It is re-colored so that you can easily distinguish in case you have both at the same time. Kids also feel themselves special, since their NewPipe has a special color.
- As in the official NewPipe, children won't be exposed to YouTube ads.

## Usage

- Install the app from apk file.
- Search the content (video, channel, playlist) by prefixing the search term with above mentioned password. When you find, save them.
- Let kids enjoy!

## Notes

- Kids may tinker with the settings, see the password, or find a way to circumvent our measures so always keep them under supervision.
- This version is based on v0.28.2 of the upstream NewPipe.
- Build is done in debug profile.
- App is provided as is without any sort of warranties.

## License

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)  

NewPipe is Free Software: You can use, study, share, and improve it at will. Specifically you can redistribute and/or modify it under the terms of the [GNU General Public License](https://www.gnu.org/licenses/gpl.html) as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
