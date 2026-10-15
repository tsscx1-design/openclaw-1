/** Offline frame stepping preserves every frame when encoding is slower than playback. */
export class HumanoidVideoExport {
  constructor(renderer, scene, camera, controller, renderFrame, frameCamera, report) {
    Object.assign(this, { renderer, scene, camera, controller, renderFrame, frameCamera, report });
    this.running = false;
    this.cancelled = false;
  }

  async start(seconds) {
    if (this.running) throw new Error("書き出し中です");
    if (!Number.isInteger(seconds) || seconds < 1 || seconds > 30)
      throw new Error("動画は1〜30秒で指定してください");
    if (this.controller.options.motion === "bvh" && !this.controller.bvh)
      throw new Error("BVHの読み込み完了を待ってください");
    const size = this.renderer.getSize({
      set(x, y) {
        this.x = x;
        this.y = y;
        return this;
      },
    });
    const previous = {
      width: size.x,
      height: size.y,
      ratio: this.renderer.getPixelRatio(),
      background: this.scene.background,
      elapsed: this.controller.elapsed,
      paused: this.controller.options.paused,
      bonesVisible: this.controller.vrm.helper?.visible,
    };
    this.running = true;
    this.cancelled = false;
    let begun = false;
    try {
      const error = Android.beginVideo(seconds);
      if (error) throw new Error(error);
      begun = true;
      this.renderer.setPixelRatio(1);
      this.renderer.setSize(1280, 720, false);
      this.camera.aspect = 1280 / 720;
      this.camera.updateProjectionMatrix();
      this.frameCamera();
      this.controller.elapsed = 0;
      this.controller.accumulator = 0;
      this.controller.anchors.clear();
      this.controller.options.paused = false;
      if (this.controller.vrm.helper) this.controller.vrm.helper.visible = false;
      this.controller.vrm.springBoneManager?.reset();
      this.controller.accessorySprings.reset();
      const frames = seconds * 30;
      for (let index = 0; index < frames; index++) {
        if (this.cancelled || document.hidden)
          throw new Error("書き出しを中止しました。画面を開いたまま実行してください");
        this.renderFrame(index === 0 ? 0 : 1 / 30, index / 30, true);
        const png = this.renderer.domElement.toDataURL("image/png");
        if (!png.startsWith("data:image/png;base64,"))
          throw new Error("映像フレームを取得できません");
        const error = Android.videoFrame(png.slice(22), index);
        if (error) throw new Error(error);
        if (index % 30 === 0) this.report("export-progress:" + Math.round((index * 100) / frames));
        await new Promise((resolve) => requestAnimationFrame(resolve));
      }
      if (this.cancelled) throw new Error("書き出しを中止しました");
      const finishError = Android.finishVideo();
      if (finishError) throw new Error(finishError);
      begun = false;
    } finally {
      if (begun) Android.cancelVideo();
      this.running = false;
      this.controller.elapsed = previous.elapsed;
      this.controller.options.paused = previous.paused;
      if (this.controller.vrm.helper) this.controller.vrm.helper.visible = previous.bonesVisible;
      this.controller.anchors.clear();
      this.controller.accumulator = 0;
      this.renderer.setPixelRatio(previous.ratio);
      this.renderer.setSize(previous.width, previous.height, false);
      this.camera.aspect = previous.width / previous.height;
      this.camera.updateProjectionMatrix();
      this.frameCamera();
      this.controller.vrm.springBoneManager?.reset();
      this.controller.accessorySprings.reset();
    }
  }

  cancel() {
    this.cancelled = true;
    if (this.running) Android.cancelVideo();
  }
}
