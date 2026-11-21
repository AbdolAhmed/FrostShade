# 库内第三方图像来源

以下资源由用户提供的 ColorOS 17 PMX110 17.0.0.115 系统材料定位，只导入实际控件所需图像。它们不适用本项目的 MPL-2.0 源码声明；原厂及上游权利继续适用，见 [NOTICE](../NOTICE)。提取材料中未检出该图像附带的独立许可证，本项目不额外授予其第三方权利。

| 库内资源 | 来源包与版本 | APK 内原始条目 | 规格 | 原始文件 SHA-256 |
| --- | --- | --- | --- | --- |
| [coui_switch_loading.png](src/main/res/drawable-xxhdpi/coui_switch_loading.png) | com.android.settings 17.0.0 | res/drawable-xxhdpi-v4/switch_loading.png | xxhdpi，40×40，PNG，1,015 字节 | 0841a935c7d4c6ad9d0eaec3ccc80b5c9c9a1a71da526ae5275fc294589d67a8 |

Settings.apk SHA-256：d97df8a9227520a6f673e5d6f5efbc901b5d9d4c501d12e5d8bf717daae079d5。

原厂 `COUISwitchStyle.loadingDrawable` 引用 `switch_loading`。库内资源保留原始 PNG，随库打包。
- `Button` 尺寸：ColorOS 17 设置（`com.android.settings`）内置 COUI 资源 `coui_btn_large_height_min` 44dp、`coui_btn_large_width_min` 132dp、`coui_btn_padding_horizontal` 12dp、`coui_btn_padding_vertical` 8dp、`coui_btn_text_size` 16sp；`coui_btn_small_height_min` 28dp、`coui_btn_small_width_min` 52dp、`coui_small_btn_padding_horizontal` 8dp、`coui_small_btn_padding_victical` 6dp、`coui_btn_small_text_size` 14sp；圆角 `coui_btn_drawable_radius_large` 21.5dp、`coui_btn_drawable_radius_small` 13dp（约为高度一半，按胶囊形绘制）。
- `TextField` 留白：同一来源的 `coui_input_edit_text_no_title_padding_top/bottom` 15dp（独立输入），`coui_input_edit_text_no_title_padding_top/bottom_inPreference` 13dp（分组行内，含行本身的上下留白）；`coui_inputView_edittext_content_minheight` 22dp、`coui_input_preference_stroke_width` 0.33dp、`coui_edittext_text_size` 16sp。
