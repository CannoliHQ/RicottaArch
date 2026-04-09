<div align="center">

<img src=".github/resources/ricotta.png" width="256px" alt="A tub of Ricotta!">
<h3 style="font-size:35px; padding-top:0px; padding-bottom:0px; margin-bottom: 0px; margin-top: 5px;">
RicottaArch
</h3>

<h4 style="font-size:18px; padding-top:0px; margin-top:0px;">Don't tell Nonna it's store-bought.</h4>

<a href="LICENSE"><img src="https://img.shields.io/github/license/CannoliHQ/RicottaArch?style=for-the-badge&color=0AB9E6" alt="License"></a>
<a href="https://github.com/CannoliHQ/RicottaArch/stargazers"><img src="https://img.shields.io/github/stars/CannoliHQ/RicottaArch?style=for-the-badge&color=0AB9E6" alt="Stars"></a>
<a href="https://github.com/CannoliHQ/RicottaArch/releases"><img src="https://img.shields.io/github/downloads/CannoliHQ/RicottaArch/total?style=for-the-badge&color=0AB9E6" alt="Downloads"></a>
<a href="https://cannoli.dev"><img src="https://img.shields.io/badge/Docs-cannoli.dev-0AB9E6?style=for-the-badge" alt="Docs"></a>

</div>

---

RicottaArch is a fork of RetroArch intended for use with [Cannoli](https://cannoli.dev).

The purpose of this fork is to add in a custom menu driver that replicates Cannoli's In-Game Menu.

The menu driver, aptly named `cannoli`, uses the default menu driver `Ozone` for all UI interactions. It only deviates
to draw a custom IGM when the menu button is pressed.

## Current Additional Features

RicottaArch currently also has two additional features, both have been submitted as a PR to RetroArch.

- [Add 'Menu Toggle Without Hotkey Enable' option](https://github.com/libretro/RetroArch/pull/18785)
- [Query Installed Cores on Android](https://github.com/libretro/RetroArch/pull/18870)

That's really it. It's RetroArch with a dumb name so you can install it alongside an official install.