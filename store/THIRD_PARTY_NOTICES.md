# Third-Party Notices — AI Space Cleaner

AI Space Cleaner includes the following third-party open-source components. Each is used
unmodified (except where noted) and is licensed under the terms below. This file satisfies the
attribution requirements of those licenses; the same notices are shown inside the app under
**Settings → Open-source licenses**.

---

## Apache License 2.0

The components below are licensed under the Apache License, Version 2.0
(https://www.apache.org/licenses/LICENSE-2.0). You may obtain a copy of the License at that URL.
Unless required by applicable law or agreed to in writing, software distributed under the License
is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.

- **Android Jetpack / AndroidX libraries** (Core-KTX, Lifecycle, Activity, Navigation) —
  © The Android Open Source Project.
- **Jetpack Compose** (UI, Material 3, Material Icons, Foundation) —
  © The Android Open Source Project.
- **AndroidX Media3 (ExoPlayer)** — © The Android Open Source Project / Google LLC.
- **Kotlin standard library & Kotlin Coroutines** — © JetBrains s.r.o. and contributors.
- **TensorFlow Lite** — © The TensorFlow Authors.
- **MobileNet v1 image-classification model** (`mobilenet_v1_1.0_224_quant.tflite`) —
  © Google LLC. Distributed by the TensorFlow project under Apache 2.0. The model is bundled
  unmodified and runs entirely on-device; it was pre-trained on the ImageNet dataset for
  general image classification and is used here to assist photo cleanup. No image data or
  inference result ever leaves the device.

---

## Notes

- No component here transmits data off the device. AI Space Cleaner declares no INTERNET
  permission.
- If you believe a component is missing from this list, contact varunkumarmuppuri@gmail.com and
  it will be added.
