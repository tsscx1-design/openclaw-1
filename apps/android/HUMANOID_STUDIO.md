# Preview reusable humanoid motion on Android

The 3D motion studio applies the same humanoid movement to different imported
VRM models. It preserves their authored bone lengths, proportions, and skin
weights. Upright static GLB models can also receive an estimated skeleton.
Original model files remain unchanged. Applied rig corrections and the rig enablement
state are saved on the Android device for each gateway, character, and model.
Other preview controls are held for the open studio session.

## Open the studio

1. Open a character's **Live2D・3Dモデル** screen.
2. Import a rigged VRM or an upright static GLB using the existing import controls.
3. Choose **共通骨格で動作・揺れを試す** on its model card.
4. Choose a motion and another VRM from the studio's model list to reuse the
   movement. The list contains VRM and GLB models imported for this character;
   additional appearances can be imported as separate models here.

The viewer uses the existing pinned Three.js and three-vrm libraries. Internet
access is required to load those libraries. A GLB initially appears as a static model until auto rigging is enabled.

## Motion and expressions

Built-in procedural motions are standing, walking, running, waving, sitting,
and looking around. Playback speed ranges from 0.25 to 2. Walking uses a bounded
leg solver for foot contact, and ground correction uses the target's actual
foot positions. These are basic preview movements, not captured performances.

Expression controls select normal, happy, angry, sad, relaxed, and surprised.
The mouth toggle previews mouth movement without generating or playing audio.
Expression availability depends on the VRM's expression definitions.

## Import a BVH performance

Choose **BVH動作を読み込む** and select a BVH file no larger than 5 MB. It must
use Y-up coordinates and a standard humanoid T-pose reference. Bone names are
matched to normalized VRM names or common BVH aliases. The importer requires
hips and both feet, reports the mapped bone count, and rejects missing required
mapping. Unmapped optional bones retain their target rest pose.

Source world rotations are converted through the source rest orientation and
target rest orientation. Root translation is scaled by hip-to-foot length;
target bone offsets and skin weights remain intact. Z-up data, arbitrary axis
conventions, nonhumanoid rigs, and reference poses other than T-pose require
conversion before import. BVH files do not supply facial expressions.

Imported BVH data stays in memory for this studio session and can be applied to
the other VRMs in its model list. A later import replaces the current motion;
an older pending import cannot overwrite a newer one.

## Physics and accessories

The physics toggle controls the VRM's spring bones and the studio's added
earring springs. Hair and clothing motion, including collision, use the spring
chains and collider groups authored in the VRM. A model without those chains
does not gain simulated hair or clothing automatically.

Flexibility ranges from 0.25 to 2 and adjusts spring stiffness and drag relative
to the original values. Physics advances in fixed 1/120-second steps with a
bounded catch-up interval. The studio does not implement cloth mesh simulation
or rigid body collisions.

Simple hats, glasses, and earrings attach to the head using model-relative
dimensions. Size and vertical/forward offsets can be adjusted to fit the model.
Earrings use spring joints and the model's existing collider groups. Removing an
accessory also removes its spring joints and disposes its generated geometry.

## Estimate a skeleton for a static GLB

Choose **自動で骨を付ける** for an upright humanoid GLB in a separated-limb
T-pose or A-pose, with Y-up coordinates and no background or pedestal. The
estimator supports up to 100,000 vertices. Already skinned GLBs should be
converted to VRM; active morph deformations must be applied before import.

The estimator fits a humanoid skeleton to the model bounds and assigns each
vertex normalized weights from the four closest bone segments. It retains the
source geometry and materials and generates a separate skinned preview. This
is geometric estimation, not learned anatomical segmentation: unusual poses,
body shapes, and clothing can need correction or a dedicated rigging tool.

Adjust shoulder width, hip height, knee height, and the source arm angle, then
choose **補正を適用して保存**. Rebinding occurs on application rather than every
slider update. **骨格を表示して確認** displays the inferred bones; **骨を外して
元モデルを表示** restores the original static preview. Enabling the rig saves the
current corrections; applying corrections saves the new values. Disabling the rig
saves its disabled state while retaining the last applied corrections. Slider edits
alone are not saved. Reopening the studio or returning to a model restores its
saved corrections and enablement state, including after restarting the app.

Storage uses the existing Room `client-state.db` database and its
`client_state_metadata` table, with `humanoid-rig:` keys containing a structured
gateway/character/model identity and versioned, bounded correction values. No
schema migration is required. Save failures retain the previous applied preview
and offer another application attempt; load failures prevent overwriting stored
values and offer **補正値を再読み込み**. These settings remain local to this device;
they are not synchronized through the Gateway. Clearing app data removes them.
Model deletion does not currently remove its correction record; a different model
ID has separate settings.

Inferred bones and weights are regenerated locally from the original model using
the restored corrections. They are not exported as a rigged model file. The
resulting motion can be exported as a movie.

This estimator supplies body bones, not finger, facial, hair, or clothing bones.
Facial controls are disabled for GLBs. Added earrings retain their own springs;
static GLBs do not acquire hair or clothing physics automatically.

## Export MP4 with optional audio

Choose 5, 10, or 30 seconds and **1280×720・30fpsでMP4を作る**. Keep the studio
visible until completion, then choose a destination in Android's document
picker. Canceling the destination picker retains the completed video so that
**完成したMP4を保存する** can retry saving. Closing the studio removes its
unsaved temporary video and selected temporary audio.

The exporter starts motion at time zero and renders every frame at a fixed
30-fps timestep. It feeds lossless PNG frames into the Android H.264 encoder at
12 Mbps and writes an MP4 container. This avoids dropped frames when rendering
or encoding is slower than playback. It also means export can take longer than
the clip's playback duration. Preview selections are locked while exporting;
**書き出しを中止** discards the incomplete output. Backgrounding the studio also
cancels export. Preview timeline and display size are restored afterward.

To include sound, choose AAC audio in an M4A or MP4 container, up to 30 MB and
one or two channels. The compressed audio is copied into the video from its
first sample. Audio longer than the movie is cut at the video duration; shorter
audio is not looped. WAV, MP3, encrypted audio, and other codecs are not
converted by this exporter. The mouth toggle is a visual preview, not phoneme
synchronization to the selected audio.

The preview skeleton overlay is omitted from exported movies. No AI video
service, screen capture permission, Gateway restart, or new dependency is
required. The native encoder must support flexible YUV image input; unsupported
or stalled encoding fails visibly and removes the incomplete artifact.

## Current limits and verification

The studio does not integrate with AI video generation or swap clothing meshes.
Switching models restores that model's rig corrections, preserves other control
selections, and restarts the motion timeline.

Android Kotlin compilation and JavaScript syntax checks passed. Visual motion,
BVH retargeting accuracy, auto rig deformation, MP4/audio playback, device
performance, and physics behavior still require
inspection with representative rigged models on a device. No visual quality or
speed comparison has been measured.

The implementation uses the public
[VRM humanoid API](https://pixiv.github.io/three-vrm/docs/classes/three-vrm.VRMHumanoid.html),
[spring bone API](https://pixiv.github.io/three-vrm/docs/classes/three-vrm-springbone.VRMSpringBoneManager.html),
and [Three.js BVH loader](https://threejs.org/docs/pages/BVHLoader.html).
