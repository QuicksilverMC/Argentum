# Argentum
Argentum is a client performance mod for Ornithe 1.8.9, based on the [Celeritas](https://git.taumc.org/embeddedt/celeritas) rendering engine.

## Features
A non-exhaustive list of features that currently exist:

- Rewritten terrain meshing from Celeritas
- Entity rendering:
  - Instancing for players, mobs, animals (including attachments such as armor, arrows, etc)
  - Optimized nametags
- Block entity rendering:
  - Instancing for most block entities
  - Baking for more select others
- Item rendering:
  - Faster enchantment glinting
  - Item baking in GUIs
- Faster and less buggy font rendering
- Optimized cloud, rain, and snow rendering
- Optimizations to select HUD elements
- Entity and particle occlusion culling
- A Celeritas-based video settings menu

A companion mod also exists under the [`extras`](/extras) folder, providing extra rendering customization and eye candy.

The [`cera`](/cera) subproject reimplements MCPatcher/OptiFine resource pack extensions.

## License
All Rights Reserved.

The Celeritas jar Argentum bundles remains licensed under the [GNU Lesser General Public License, version 3](https://www.gnu.org/licenses/lgpl-3.0.html).
