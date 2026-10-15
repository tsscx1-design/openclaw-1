# Motion comic generation

This local OpenClaw workflow generates key images, video cuts, and a composed
movie. The Gateway owns job creation; Android exposes character settings and
saved movies. These scripts require the operator's existing media tools.

## Reuse a movie with another voice

In Android, open a completed motion comic and choose **映像を活かして声を変更**.
Select a saved voice or enter a voice description. The result is a separate job.
The original job and its files remain intact.

The `media.generate` request uses `kind: "motion"`, `motionRevoiceJobId`, and
either `voiceId` or `voiceCaption`. This operation copies the source workspace,
regenerates the soundtrack, and composes the movie from its existing images and
video cuts. Missing source assets stop the job instead of triggering video
generation. Dialogue duration can change the length of composed cuts.

Voice audio identities include the model, reference audio contents, and spoken
text. Changing a voice therefore cannot silently reuse audio for the old voice.

## Character proportions and secondary movement

The character library's **体格・揺れを調整する** section accepts these optional
values. Empty fields leave the corresponding behavior unspecified.

| Fields                                         | Range   | Meaning                                  |
| ---------------------------------------------- | ------- | ---------------------------------------- |
| `shoulderScale`, `hipScale`                    | 0.6–1.6 | Width relative to standard proportions   |
| `armScale`, `legScale`, `torsoScale`           | 0.7–1.3 | Length relative to standard proportions  |
| `chestMotion`, `hairMotion`, `accessoryMotion` | 0–1     | Amount of secondary movement             |
| `chestSoftness`                                | 0–1     | Restrained through soft deformation      |
| `chestDamping`                                 | 0–1     | Gradual through quick settling           |
| `clothingSupport`                              | 0–1     | How strongly clothing restrains movement |

The existing character update method accepts `motionProfile`. Profiles use the
existing SQLite `config_machine_state` store under
`media.character-motion.<character-id>`; no schema migration or replacement of
existing character files is required. Deleting a character removes its profile.

These values guide the video model for newly generated cuts of the main
character. They do not implement skeleton retargeting, inverse kinematics, or
physical simulation. Revoicing preserves the original visual assets and does
not apply new movement settings.

## Quality and processing changes

- Key images and their derivatives share one image-model load. Sources must
  precede their derivatives in the job's key list.
- Identical negative prompts share encoded embeddings within that process.
- Image resolution, inference steps, and video frame settings are preserved.
- RIFE intermediates are identified by source video bytes and interpolation
  script contents. Repeated cuts can reuse them; redraws cannot reuse stale frames.
- Video cuts and interpolation intermediates become reusable only after their
  producer completes successfully. A failed cut stops the job.
- Composition copies the final video stream when there are no text overlays.
  Text overlays use CRF 16 to reduce compression loss.
- Continuing or redrawing a draft uses a separate workspace and preserves the
  original seed and source job.

Compilation and syntax checks do not establish a speedup or visual quality.
Comparative generation measurements are still required. Full character swaps
through a shared skeleton also require a rigged asset and rendering pipeline.

## Runtime copies

The deployed copies are documented in [the external tools mapping](../external/README.md).
Update the repository and deployed copies together when no motion generation
is running. Do not restart the Gateway as part of a script synchronization.
