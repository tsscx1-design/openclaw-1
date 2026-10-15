import * as THREE from "three";

const worldQ = (node) => node.getWorldQuaternion(new THREE.Quaternion());
const worldP = (node) => node.getWorldPosition(new THREE.Vector3());

/** Geometric fitting for an upright, separated-limb T/A pose; source assets are immutable. */
export class HumanoidAutoRig {
  constructor(scene, profile) {
    for (const [field, min, max] of [
      ["shoulderWidth", 0.16, 0.32],
      ["hipHeight", 0.42, 0.62],
      ["kneeHeight", 0.18, 0.34],
      ["armAngle", 0, 60],
    ]) {
      if (!Number.isFinite(profile[field]) || profile[field] < min || profile[field] > max)
        throw new Error("骨格の補正値が範囲外です：" + field);
    }
    scene.updateMatrixWorld(true);
    const inverse = scene.matrixWorld.clone().invert();
    const meshes = [];
    let vertices = 0;
    const bounds = new THREE.Box3();
    scene.traverseVisible((node) => {
      if (!node.isMesh) return;
      if (node.isSkinnedMesh) throw new Error("骨格付きGLBはVRMに変換して使ってください");
      if (node.morphTargetInfluences?.some((value) => value !== 0))
        throw new Error("変形中のGLBは、変形を適用してから書き出し直してください");
      const position = node.geometry.getAttribute("position");
      if (!position || position.itemSize !== 3 || !node.visible) return;
      vertices += position.count;
      if (vertices > 100_000)
        throw new Error(
          "自動骨付けは10万頂点までです。元モデルの骨付けを3D編集ソフトで行ってください",
        );
      const matrix = inverse.clone().multiply(node.matrixWorld);
      node.geometry.computeBoundingBox();
      bounds.union(node.geometry.boundingBox.clone().applyMatrix4(matrix));
      meshes.push({ source: node, matrix, visible: node.visible });
    });
    if (!meshes.length) throw new Error("骨を付けられるメッシュがありません");
    const size = bounds.getSize(new THREE.Vector3());
    if (
      !(size.y > 0.001) ||
      size.x / size.y < 0.25 ||
      size.x / size.y > 1.5 ||
      size.z / size.y > 0.7
    ) {
      throw new Error(
        "直立した人型のTポーズ・Aポーズを使ってください。Y軸が上で、背景や台座を含めないモデルが必要です",
      );
    }
    this.scene = scene;
    this.sources = meshes;
    this.rawRoot = new THREE.Group();
    this.normalRoot = new THREE.Group();
    this.raw = {};
    this.normal = {};
    this.rest = {};
    this.generated = [];
    this.height = size.y;
    this.profile = { ...profile };
    const h = size.y,
      center = bounds.getCenter(new THREE.Vector3());
    const point = (x, y, z = 0) =>
      new THREE.Vector3(center.x + x * h, bounds.min.y + y * h, center.z + z * h);
    const joints = [
      ["hips", null, point(0, profile.hipHeight)],
      ["spine", "hips", point(0, 0.66)],
      ["chest", "spine", point(0, 0.74)],
      ["upperChest", "chest", point(0, 0.81)],
      ["neck", "upperChest", point(0, 0.86)],
      ["head", "neck", point(0, 0.91)],
    ];
    const angle = THREE.MathUtils.degToRad(profile.armAngle);
    for (const [side, sign] of [
      ["left", 1],
      ["right", -1],
    ]) {
      const shoulder = profile.shoulderWidth / 2;
      joints.push(
        [side + "Shoulder", "upperChest", point(sign * shoulder * 0.75, 0.81)],
        [side + "UpperArm", side + "Shoulder", point(sign * shoulder, 0.81)],
        [
          side + "LowerArm",
          side + "UpperArm",
          point(sign * (shoulder + Math.cos(angle) * 0.15), 0.81 - Math.sin(angle) * 0.15),
          point(sign * (shoulder + 0.15), 0.81),
        ],
        [
          side + "Hand",
          side + "LowerArm",
          point(sign * (shoulder + Math.cos(angle) * 0.29), 0.81 - Math.sin(angle) * 0.29),
          point(sign * (shoulder + 0.29), 0.81),
        ],
        [side + "UpperLeg", "hips", point(sign * 0.065, profile.hipHeight)],
        [side + "LowerLeg", side + "UpperLeg", point(sign * 0.065, profile.kneeHeight)],
        [side + "Foot", side + "LowerLeg", point(sign * 0.065, 0.045)],
        [side + "Toes", side + "Foot", point(sign * 0.065, 0.025, 0.07)],
      );
    }
    this.joints = joints;
    const positions = new Map(joints.map((row) => [row[0], row[2]]));
    const normalizedPositions = new Map(joints.map((row) => [row[0], row[3] ?? row[2]]));
    const orientations = new Map(
      joints.map(([name]) => [
        name,
        new THREE.Quaternion().setFromEuler(
          new THREE.Euler(
            0,
            0,
            /^(left|right)(UpperArm|LowerArm|Hand)$/.test(name)
              ? name.startsWith("left")
                ? -angle
                : angle
              : 0,
          ),
        ),
      ]),
    );
    try {
      for (const [name, parentName, position] of joints) {
        const raw = new THREE.Bone();
        raw.name = "auto-" + name;
        const normal = new THREE.Object3D();
        normal.name = "normalized-" + name;
        const parentQ = parentName ? orientations.get(parentName) : new THREE.Quaternion();
        raw.position
          .copy(position)
          .sub(parentName ? positions.get(parentName) : new THREE.Vector3())
          .applyQuaternion(parentQ.clone().invert());
        raw.quaternion.copy(parentQ.clone().invert().multiply(orientations.get(name)));
        normal.position
          .copy(normalizedPositions.get(name))
          .sub(parentName ? normalizedPositions.get(parentName) : new THREE.Vector3());
        (parentName ? this.raw[parentName] : this.rawRoot).add(raw);
        (parentName ? this.normal[parentName] : this.normalRoot).add(normal);
        this.raw[name] = raw;
        this.normal[name] = normal;
      }
      scene.add(this.rawRoot, this.normalRoot);
      scene.updateMatrixWorld(true);
      for (const [name, raw] of Object.entries(this.raw)) {
        this.rest[name] = {
          rawPosition: raw.position.clone(),
          normalPosition: this.normal[name].position.clone(),
        };
      }
      this.skeleton = new THREE.Skeleton(Object.values(this.raw));
      this.skeleton.calculateInverses();
      this.helper = new THREE.SkeletonHelper(this.rawRoot);
      this.helper.visible = false;
      scene.add(this.helper);
      const segments = joints.map(([name, , start]) => {
        const child = joints.find((row) => row[1] === name);
        const end =
          child?.[2] ??
          start.clone().add(new THREE.Vector3(0, name === "head" ? h * 0.08 : h * 0.015, 0));
        return { start, end, lengthSq: start.distanceToSquared(end) };
      });
      for (const item of meshes) {
        const geometry = item.source.geometry.clone().applyMatrix4(item.matrix);
        const mesh = new THREE.SkinnedMesh(geometry, item.source.material);
        this.generated.push(mesh);
        const position = geometry.getAttribute("position");
        const indices = new Uint16Array(position.count * 4);
        const weights = new Float32Array(position.count * 4);
        const vertex = new THREE.Vector3(),
          direction = new THREE.Vector3(),
          projected = new THREE.Vector3();
        for (let index = 0; index < position.count; index++) {
          vertex.fromBufferAttribute(position, index);
          const nearest = [];
          for (let bone = 0; bone < segments.length; bone++) {
            const segment = segments[bone];
            direction.copy(segment.end).sub(segment.start);
            const t =
              segment.lengthSq > 0
                ? THREE.MathUtils.clamp(
                    projected.copy(vertex).sub(segment.start).dot(direction) / segment.lengthSq,
                    0,
                    1,
                  )
                : 0;
            projected.copy(segment.start).addScaledVector(direction, t);
            const distance = projected.distanceToSquared(vertex);
            nearest.push({ bone, weight: 1 / Math.pow(Math.max(distance, h * h * 0.00001), 2) });
          }
          nearest.sort((a, b) => b.weight - a.weight);
          const sum = nearest.slice(0, 4).reduce((total, row) => total + row.weight, 0);
          for (let slot = 0; slot < 4; slot++) {
            indices[index * 4 + slot] = nearest[slot].bone;
            weights[index * 4 + slot] = nearest[slot].weight / sum;
          }
        }
        geometry.setAttribute("skinIndex", new THREE.Uint16BufferAttribute(indices, 4));
        geometry.setAttribute("skinWeight", new THREE.Float32BufferAttribute(weights, 4));
        this.rawRoot.add(mesh);
        scene.updateMatrixWorld(true);
        mesh.bind(this.skeleton, mesh.matrixWorld.clone());
        mesh.frustumCulled = false;
      }
      for (const item of meshes) item.source.visible = false;
      const adapter = this;
      this.humanoid = {
        normalizedHumanBones: Object.fromEntries(
          Object.keys(this.normal).map((name) => [name, { node: this.normal[name] }]),
        ),
        getNormalizedBoneNode: (name) => this.normal[name] ?? null,
        getRawBoneNode: (name) => this.raw[name] ?? null,
        resetNormalizedPose() {
          for (const [name, bone] of Object.entries(adapter.normal)) {
            bone.position.copy(adapter.rest[name].normalPosition);
            bone.quaternion.identity();
          }
        },
        update() {
          adapter.updateBones();
        },
      };
      this.expressionManager = null;
      this.springBoneManager = null;
    } catch (error) {
      this.dispose();
      throw error;
    }
  }

  updateBones() {
    this.normalRoot.updateMatrixWorld(true);
    for (const [name, raw] of Object.entries(this.raw)) {
      const rest = this.rest[name];
      // Bind matrices retain the authored A pose; normalized rotations define the target T-pose axes.
      const desiredWorld = worldQ(this.normal[name]);
      raw.quaternion.copy(worldQ(raw.parent).invert().multiply(desiredWorld));
      raw.position.copy(rest.rawPosition);
      if (name === "hips") {
        const delta = worldP(this.normal[name]).sub(
          this.normalRoot.localToWorld(rest.normalPosition.clone()),
        );
        const target = worldP(raw).add(delta);
        raw.position.copy(raw.parent.worldToLocal(target));
      }
      raw.updateMatrixWorld(true);
    }
    this.skeleton.update();
  }

  update() {
    this.updateBones();
  }

  dispose() {
    for (const mesh of this.generated) {
      mesh.removeFromParent();
      mesh.geometry.dispose();
    }
    this.rawRoot.removeFromParent();
    this.normalRoot.removeFromParent();
    this.helper?.removeFromParent();
    this.helper?.geometry.dispose();
    this.helper?.material.dispose();
    this.skeleton?.dispose();
    for (const item of this.sources) item.source.visible = item.visible;
  }
}
