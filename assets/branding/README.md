# Sticker Kaleidoscope 图标

生成方式：内置 `image_gen` 工具；生成日期：2026-10-20。
设计为折角贴纸组成的万花筒，沿用应用的深色、鼠尾草绿和淡紫配色。

## 文件

- `app-icon-source.png`：工具生成的原始 PNG，保留透明背景。
- `../../composeApp/src/commonMain/composeResources/drawable/app_icon.png`：512 px 应用图标，用于窗口、标题栏及任务栏。
- `../../composeApp/src/desktopMain/resources/app-icon.ico`：Windows EXE 图标，包含 16/24/32/48/64/128/256 px 尺寸。

运行 `python scripts/build-icons.py` 可重新生成应用 PNG 与 ICO，需要 Pillow。
转换仅处理尺寸与文件封装，不修改图案；原始文件保留。

## 实际生成提示词

```text
Use case: logo-brand. Asset type: a single production Windows desktop application icon for Sticker Kaleidoscope, a personal meme/sticker organizer. Create a polished flat illustrated icon, centered square composition, genuinely transparent outside the icon. Subject: a playful folded sticker arranged into a compact kaleidoscope rosette: four chunky overlapping rounded sticker petals around a small diamond-shaped negative center, one clearly visible peeled paper corner. Use the app's existing muted sage/mint green and warm off-white on a deep charcoal rounded-square tile, with a restrained lavender accent. Friendly, crisp silhouette readable at 24px, few bold shapes, subtle paper depth only, generous but small safe margins. Straight-on view, no perspective mockup, no text, no letters, no watermark, no thin outlines, no Java or coffee motif. Deliver one icon only, not an icon sheet.
```
# Branding assets

