import { VRMSpringBoneJoint, VRMSpringBoneManager } from "@pixiv/three-vrm";
import * as THREE from "three";
import { BVHLoader } from "three/addons/loaders/BVHLoader.js";

const MOTIONS = new Set(["idle", "walk", "run", "wave", "sit", "look", "bvh"]);
const ALIASES = {
  hips: ["hips", "hip", "pelvis"],
  spine: ["spine", "lowerback"],
  chest: ["spine1", "chest"],
  upperChest: ["upperchest", "spine2", "spine3"],
  leftShoulder: ["leftshoulder", "lclavicle"],
  rightShoulder: ["rightshoulder", "rclavicle"],
  leftToes: ["lefttoebase", "ltoes"],
  rightToes: ["righttoebase", "rtoes"],
  neck: ["neck"],
  head: ["head"],
  leftUpperArm: ["leftarm", "lupperarm"],
  rightUpperArm: ["rightarm", "rupperarm"],
  leftLowerArm: ["leftforearm", "llowerarm"],
  rightLowerArm: ["rightforearm", "rlowerarm"],
  leftHand: ["lefthand", "lhand"],
  rightHand: ["righthand", "rhand"],
  leftUpperLeg: ["leftupleg", "lthigh"],
  rightUpperLeg: ["rightupleg", "rthigh"],
  leftLowerLeg: ["leftleg", "lshin"],
  rightLowerLeg: ["rightleg", "rshin"],
  leftFoot: ["leftfoot", "lfoot"],
  rightFoot: ["rightfoot", "rfoot"],
};
const worldQ = (node) => node.getWorldQuaternion(new THREE.Quaternion());
const worldP = (node) => node.getWorldPosition(new THREE.Vector3());
const normalizedName = (name) =>
  name
    .toLowerCase()
    .replace(/^mixamorig:?/, "")
    .replace(/[^a-z0-9]/g, "");

// VRM normalized bones retain the target's authored lengths and skin weights.
export class HumanoidMotion {
  constructor(vrm) {
    this.vrm = vrm;
    this.bones = {};
    this.rest = {};
    vrm.humanoid.resetNormalizedPose();
    vrm.scene.updateMatrixWorld(true);
    for (const name of Object.keys(vrm.humanoid.normalizedHumanBones)) {
      const bone = vrm.humanoid.getNormalizedBoneNode(name);
      this.bones[name] = bone;
      this.rest[name] = {
        position: bone.position.clone(),
        quaternion: bone.quaternion.clone(),
        world: worldQ(bone),
      };
    }
    for (const name of [
      "hips",
      "head",
      "leftUpperLeg",
      "rightUpperLeg",
      "leftLowerLeg",
      "rightLowerLeg",
      "leftFoot",
      "rightFoot",
    ]) {
      if (!this.bones[name]) throw new Error("共通骨格に必要な部位がありません：" + name);
    }
    this.height = worldP(this.bones.head).distanceTo(worldP(this.bones.leftFoot));
    if (!(this.height > 0.01)) throw new Error("モデルの身長を取得できません");
    this.floor = Math.min(worldP(this.bones.leftFoot).y, worldP(this.bones.rightFoot).y);
    this.springDefaults = new Map();
    for (const joint of vrm.springBoneManager?.joints ?? []) {
      this.springDefaults.set(joint, {
        stiffness: joint.settings.stiffness,
        dragForce: joint.settings.dragForce,
      });
    }
    this.options = { motion: "idle", speed: 1, physics: true, flexibility: 1, accessory: "none" };
    this.elapsed = 0;
    this.accumulator = 0;
    this.loadVersion = 0;
    this.anchors = new Map();
    this.accessory = null;
    this.accessorySprings = new VRMSpringBoneManager();
    this.headBasis = worldQ(vrm.humanoid.getRawBoneNode("head"))
      .invert()
      .multiply(worldQ(this.bones.head));
    this.headScale = vrm.humanoid.getRawBoneNode("head").getWorldScale(new THREE.Vector3());
    if (
      [this.headScale.x, this.headScale.y, this.headScale.z].some(
        (value) => !Number.isFinite(value) || value <= 0,
      )
    )
      throw new Error("モデルの頭部スケールが不正です");
    this.disposed = false;
  }

  configure(options) {
    if (!MOTIONS.has(options.motion)) throw new Error("この動作は使えません");
    if (!Number.isFinite(options.speed) || options.speed < 0.25 || options.speed > 2)
      throw new Error("再生速度が範囲外です");
    if (
      !Number.isFinite(options.flexibility) ||
      options.flexibility < 0.25 ||
      options.flexibility > 2
    )
      throw new Error("揺れ設定が範囲外です");
    if (
      !Number.isFinite(options.accessoryScale) ||
      options.accessoryScale < 0.5 ||
      options.accessoryScale > 1.5
    )
      throw new Error("装飾品の大きさが範囲外です");
    for (const value of [options.accessoryY, options.accessoryZ])
      if (!Number.isFinite(value) || Math.abs(value) > 0.1)
        throw new Error("装飾品の位置が範囲外です");
    if (this.options.motion !== options.motion) this.anchors.clear();
    if (this.options.accessory !== options.accessory) this.setAccessory(options.accessory);
    if (this.accessory) {
      this.accessory.scale.set(
        options.accessoryScale / this.headScale.x,
        options.accessoryScale / this.headScale.y,
        options.accessoryScale / this.headScale.z,
      );
      this.accessory.position.set(
        0,
        (this.height * options.accessoryY) / this.headScale.y,
        (this.height * options.accessoryZ) / this.headScale.z,
      );
    }
    this.options = { ...options };
    for (const [joint, original] of this.springDefaults) {
      joint.settings.stiffness = original.stiffness / options.flexibility;
      joint.settings.dragForce = THREE.MathUtils.clamp(
        original.dragForce / options.flexibility,
        0,
        1,
      );
    }
    for (const joint of this.accessorySprings.joints) {
      joint.settings.stiffness = 1 / options.flexibility;
      joint.settings.dragForce = THREE.MathUtils.clamp(0.4 / options.flexibility, 0, 1);
    }
    if (!options.physics) this.vrm.springBoneManager?.reset();
  }

  async loadBvh(url) {
    const version = ++this.loadVersion;
    const response = await fetch(url);
    if (!response.ok) throw new Error("動作ファイルを読み込めません");
    const text = await response.text();
    if (text.length > 5_000_000) throw new Error("動作ファイルは5MBまでです");
    const parsed = new BVHLoader().parse(text);
    const sourceRoot = parsed.skeleton.bones[0];
    sourceRoot.updateMatrixWorld(true);
    const mapping = [];
    const usedSources = new Set();
    for (const target of Object.keys(this.bones)) {
      const aliases = [...(ALIASES[target] ?? [])];
      const finger =
        /^(left|right)(Thumb|Index|Middle|Ring|Little)(Metacarpal|Proximal|Intermediate|Distal)$/.exec(
          target,
        );
      if (finger) {
        const segments =
          finger[2] === "Thumb"
            ? { Metacarpal: 1, Proximal: 2, Distal: 3 }
            : { Proximal: 1, Intermediate: 2, Distal: 3 };
        const digit = finger[2] === "Little" ? "pinky" : finger[2].toLowerCase();
        aliases.push(finger[1] + "hand" + digit + segments[finger[3]]);
      }
      const source = parsed.skeleton.bones.find(
        (bone) =>
          !usedSources.has(bone) &&
          [normalizedName(target), ...aliases].includes(normalizedName(bone.name)),
      );
      if (source) {
        mapping.push({ target, source, rest: worldQ(source).invert() });
        usedSources.add(source);
      }
    }
    if (
      !mapping.some((row) => row.target === "hips") ||
      !mapping.some((row) => row.target === "leftFoot") ||
      !mapping.some((row) => row.target === "rightFoot")
    ) {
      throw new Error(
        "BVHの腰・左右の足を対応付けできません。Y軸が上の標準人型BVHを使ってください",
      );
    }
    const hip = mapping.find((row) => row.target === "hips").source;
    const foot = mapping.find((row) => row.target === "leftFoot").source;
    const sourceHeight = worldP(hip).distanceTo(worldP(foot));
    const targetHeight = worldP(this.bones.hips).distanceTo(worldP(this.bones.leftFoot));
    if (
      !(sourceHeight > 0.0001) ||
      !(parsed.clip.duration > 0) ||
      !Number.isFinite(parsed.clip.duration)
    )
      throw new Error("BVHの体格または再生時間が不正です");
    const depth = (node) => {
      let n = 0;
      for (let p = node.parent; p; p = p.parent) n++;
      return n;
    };
    mapping.sort((a, b) => depth(this.bones[a.target]) - depth(this.bones[b.target]));
    if (version !== this.loadVersion || this.disposed) return;
    this.bvh?.mixer.stopAllAction();
    this.bvh?.mixer.uncacheRoot(this.bvh.root);
    const mixer = new THREE.AnimationMixer(sourceRoot);
    mixer.clipAction(parsed.clip).play();
    this.bvh = {
      root: sourceRoot,
      mixer,
      mapping,
      hip,
      origin: hip.position.clone(),
      scale: targetHeight / sourceHeight,
      duration: parsed.clip.duration,
    };
    this.anchors.clear();
    return mapping.length;
  }

  rotate(name, x = 0, y = 0, z = 0) {
    const bone = this.bones[name];
    if (bone)
      bone.quaternion
        .copy(this.rest[name].quaternion)
        .multiply(new THREE.Quaternion().setFromEuler(new THREE.Euler(x, y, z)));
  }

  pose(t) {
    for (const [name, bone] of Object.entries(this.bones)) {
      bone.position.copy(this.rest[name].position);
      bone.quaternion.copy(this.rest[name].quaternion);
    }
    const motion = this.options.motion;
    if (motion === "bvh") {
      if (!this.bvh) return;
      const source = this.bvh;
      source.mixer.setTime(t % source.duration);
      source.root.updateMatrixWorld(true);
      for (const row of source.mapping) {
        const bone = this.bones[row.target];
        const delta = worldQ(row.source).multiply(row.rest);
        bone.quaternion.copy(
          worldQ(bone.parent).invert().multiply(delta).multiply(this.rest[row.target].world),
        );
        bone.updateMatrixWorld(true);
      }
      this.bones.hips.position.add(
        source.hip.position.clone().sub(source.origin).multiplyScalar(source.scale),
      );
      return;
    }
    this.rotate("leftUpperArm", 0, 0, -1.2);
    this.rotate("rightUpperArm", 0, 0, 1.2);
    this.rotate("spine", 0, 0, Math.sin(t * 1.8) * 0.015);
    if (motion === "walk" || motion === "run") {
      const run = motion === "run";
      const phase = t * (run ? 8 : 4);
      const swing = Math.sin(phase) * (run ? 0.75 : 0.4);
      this.rotate("leftUpperLeg", swing);
      this.rotate("rightUpperLeg", -swing);
      this.rotate("leftLowerLeg", Math.max(0, -Math.sin(phase)) * (run ? 1.2 : 0.7));
      this.rotate("rightLowerLeg", Math.max(0, Math.sin(phase)) * (run ? 1.2 : 0.7));
      this.rotate("leftUpperArm", -swing * 0.6, 0, -1.2);
      this.rotate("rightUpperArm", swing * 0.6, 0, 1.2);
      this.rotate("leftLowerArm", 0, run ? -0.8 : -0.2);
      this.rotate("rightLowerArm", 0, run ? 0.8 : 0.2);
    } else if (motion === "wave") {
      this.rotate("rightUpperArm", 0, 0, -0.6);
      this.rotate("rightLowerArm", 0, 0, -0.6 + Math.sin(t * 5) * 0.25);
      this.rotate("rightHand", 0, Math.sin(t * 5) * 0.3);
    } else if (motion === "sit") {
      this.rotate("leftUpperLeg", -Math.PI / 2);
      this.rotate("rightUpperLeg", -Math.PI / 2);
      this.rotate("leftLowerLeg", Math.PI / 2);
      this.rotate("rightLowerLeg", Math.PI / 2);
    } else if (motion === "look") {
      this.rotate("head", 0, Math.sin(t) * 0.65);
      this.rotate("spine", 0, Math.sin(t) * 0.15);
    }
  }

  // Bounded CCD adjusts the actual leg lengths, rather than stretching the mesh.
  solveFoot(side, target) {
    const foot = this.bones[side + "Foot"];
    for (let iteration = 0; iteration < 4; iteration++) {
      for (const name of [side + "LowerLeg", side + "UpperLeg"]) {
        const joint = this.bones[name];
        const origin = worldP(joint);
        const from = worldP(foot).sub(origin);
        const to = target.clone().sub(origin);
        if (from.lengthSq() < 1e-10 || to.lengthSq() < 1e-10) continue;
        const delta = new THREE.Quaternion().setFromUnitVectors(from.normalize(), to.normalize());
        const parent = worldQ(joint.parent);
        joint.quaternion.premultiply(parent.clone().invert().multiply(delta).multiply(parent));
        joint.updateMatrixWorld(true);
      }
    }
  }

  update(dt) {
    if (this.disposed) return;
    const elapsed = Math.min(Math.max(dt, 0), 0.1);
    if (!this.options.paused) this.elapsed += elapsed * this.options.speed;
    this.pose(this.elapsed);
    this.vrm.scene.updateMatrixWorld(true);
    const feet = [worldP(this.bones.leftFoot), worldP(this.bones.rightFoot)];
    const lift = this.floor - Math.min(feet[0].y, feet[1].y);
    // Convert the world-space ground correction to the hips parent's local frame.
    const hip = this.bones.hips;
    const a = hip.parent.worldToLocal(worldP(hip).add(new THREE.Vector3(0, lift, 0)));
    hip.position.copy(a);
    this.vrm.scene.updateMatrixWorld(true);
    if (this.options.motion === "walk") {
      for (const [index, side] of ["left", "right"].entries()) {
        const contact = Math.sin(this.elapsed * 4 + index * Math.PI) >= 0;
        if (!contact) this.anchors.delete(side);
        else {
          if (!this.anchors.has(side)) {
            const anchor = worldP(this.bones[side + "Foot"]);
            anchor.y = this.floor;
            this.anchors.set(side, anchor);
          }
          this.solveFoot(side, this.anchors.get(side));
        }
      }
    }
    // Fixed substeps keep the imported spring/collider simulation stable after stalls.
    if (elapsed === 0) {
      this.vrm.humanoid.update();
      this.vrm.expressionManager?.update();
    }
    this.accumulator += elapsed;
    while (this.accumulator >= 1 / 120) {
      if (!this.options.physics) this.vrm.springBoneManager?.reset();
      this.vrm.update(1 / 120);
      if (this.options.physics) this.accessorySprings.update(1 / 120);
      else this.accessorySprings.reset();
      if (!this.options.physics) this.vrm.springBoneManager?.reset();
      this.accumulator -= 1 / 120;
    }
  }

  setAccessory(kind) {
    if (!["none", "hat", "glasses", "earrings"].includes(kind))
      throw new Error("この装飾品は使えません");
    if (this.accessory) {
      for (const joint of [...this.accessorySprings.joints])
        this.accessorySprings.deleteJoint(joint);
      this.accessory.removeFromParent();
      this.accessory.traverse((node) => {
        node.geometry?.dispose();
        if (node.material) node.material.dispose();
      });
      this.accessory = null;
    }
    if (kind === "none") return;
    const head = this.vrm.humanoid.getRawBoneNode("head");
    const group = new THREE.Group();
    const size = this.height * 0.065;
    const material = new THREE.MeshStandardMaterial({
      color: kind === "earrings" ? 0xffd070 : 0x455579,
      metalness: 0.4,
      roughness: 0.45,
    });
    const mesh = (geometry, x, y, z) => {
      const object = new THREE.Mesh(geometry, material);
      object.position.set(x, y, z);
      group.add(object);
      return object;
    };
    if (kind === "hat") {
      mesh(new THREE.CylinderGeometry(size * 1.1, size * 1.1, size * 0.12, 32), 0, size * 1.5, 0);
      mesh(new THREE.CylinderGeometry(size * 0.8, size, size * 0.9, 32), 0, size * 2, 0);
    } else if (kind === "glasses") {
      for (const sign of [-1, 1])
        mesh(
          new THREE.TorusGeometry(size * 0.38, size * 0.035, 8, 24),
          sign * size * 0.43,
          size * 0.35,
          size * 0.8,
        );
      mesh(new THREE.BoxGeometry(size * 0.2, size * 0.04, size * 0.05), 0, size * 0.35, size * 0.8);
    } else {
      for (const sign of [-1, 1]) {
        const pivot = new THREE.Object3D();
        pivot.position.set(sign * size, 0, 0);
        group.add(pivot);
        const ring = mesh(
          new THREE.TorusGeometry(size * 0.22, size * 0.04, 8, 24),
          0,
          -size * 0.3,
          0,
        );
        group.remove(ring);
        pivot.add(ring);
        const tail = new THREE.Object3D();
        tail.position.y = -size * 0.6;
        pivot.add(tail);
        this.accessorySprings.addJoint(
          new VRMSpringBoneJoint(
            pivot,
            tail,
            { stiffness: 1, dragForce: 0.4, gravityPower: 0.1, hitRadius: size * 0.04 },
            this.vrm.springBoneManager?.colliderGroups ?? [],
          ),
        );
      }
    }
    // Accessories use normalized head coordinates even when a raw bone is rotated.
    group.quaternion.copy(this.headBasis);
    head.add(group);
    this.vrm.scene.updateMatrixWorld(true);
    this.accessorySprings.setInitState();
    this.accessory = group;
  }

  dispose() {
    this.disposed = true;
    this.loadVersion++;
    this.setAccessory("none");
    this.bvh?.mixer.stopAllAction();
    if (this.bvh) this.bvh.mixer.uncacheRoot(this.bvh.root);
    for (const [joint, original] of this.springDefaults) Object.assign(joint.settings, original);
  }
}
