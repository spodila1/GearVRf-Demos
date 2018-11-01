/* Copyright 2015 Samsung Electronics Co., LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gearvrf.arcore.aravatar;

import android.util.Log;

import org.gearvrf.GVRBoxCollider;
import org.gearvrf.GVRContext;
import org.gearvrf.GVRDirectLight;
import org.gearvrf.GVREventListeners;
import org.gearvrf.GVRLight;
import org.gearvrf.GVRMain;
import org.gearvrf.GVRPicker;
import org.gearvrf.GVRPointLight;
import org.gearvrf.GVRScene;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.animation.GVRAnimation;
import org.gearvrf.animation.GVRAnimator;
import org.gearvrf.animation.GVRAvatar;
import org.gearvrf.animation.GVRRepeatMode;
import org.gearvrf.mixedreality.GVRAnchor;
import org.gearvrf.mixedreality.GVRHitResult;
import org.gearvrf.mixedreality.GVRMixedReality;
import org.gearvrf.mixedreality.GVRPlane;
import org.gearvrf.mixedreality.GVRTrackingState;
import org.gearvrf.mixedreality.IAnchorEventsListener;
import org.gearvrf.mixedreality.IPlaneEventsListener;

public class AvatarMain extends GVRMain {
    private static String TAG = "ARAVATAR";
    private GVRContext        mContext;
    private GVRScene          mScene;
    private GVRAvatar         mAvatar;
    private GVRMixedReality   mMixedReality;
    private AssetFactory      mAssets;
    private TouchHandler      mTouchHandler;
    private SelectionHandler  mSelector;
    private GVRDirectLight    mSceneLight;
    private String            mAvatarName = "Eva";

    @Override
    public void onInit(GVRContext gvrContext)
    {
        mContext = gvrContext;
        mScene = mContext.getMainScene();
        mAssets = new AssetFactory(mContext);
        mTouchHandler = new TouchHandler();
        mSelector = new SelectionHandler(gvrContext);
        mSceneLight = mAssets.makeSceneLight(gvrContext);
        mScene.addSceneObject(mSceneLight.getOwnerObject());
        mAvatar = mAssets.selectAvatar("YBOT");
        mAssets.loadModel();
       // mAvatar = mAssets.loadAvatar(mContext, mAvatarName);
        if (mAvatar == null)
        {
            Log.e(TAG, "Avatar could not be loaded");
        }
        mAssets.initCursorController(gvrContext, mTouchHandler);
        mMixedReality = new GVRMixedReality(mContext);
        mMixedReality.registerPlaneListener(planeEventsListener);
        mMixedReality.registerAnchorListener(anchorEventsListener);
        mMixedReality.resume();
    }

    @Override
    public void onStep()
    {
        float light = mMixedReality.getLightEstimate().getPixelIntensity() * 1.5f;
        mSceneLight.setAmbientIntensity(light, light, light, 1);
        mSceneLight.setDiffuseIntensity(light, light, light, 1);
        mSceneLight.setSpecularIntensity(light, light, light, 1);
    }


    private IPlaneEventsListener planeEventsListener = new IPlaneEventsListener()
    {
        @Override
        public void onPlaneDetection(GVRPlane gvrPlane)
        {
            if (gvrPlane.getPlaneType() == GVRPlane.PlaneType.HORIZONTAL_UPWARD_FACING)
            {
                GVRSceneObject planeMesh = mAssets.createPlane(getGVRContext(), mMixedReality.getARToVRScale());

                planeMesh.attachComponent(gvrPlane);
                mScene.addSceneObject(planeMesh);
            }
        }

        @Override
        public void onPlaneStateChange(GVRPlane gvrPlane, GVRTrackingState gvrTrackingState)
        {
            gvrPlane.setEnable(gvrTrackingState == GVRTrackingState.TRACKING);
        }

        @Override
        public void onPlaneMerging(GVRPlane gvrPlane, GVRPlane gvrPlane1)
        {
        }
    };

    private IAnchorEventsListener anchorEventsListener = new IAnchorEventsListener()
    {
        @Override
        public void onAnchorStateChange(GVRAnchor gvrAnchor, GVRTrackingState gvrTrackingState)
        {
            gvrAnchor.setEnable(gvrTrackingState == GVRTrackingState.TRACKING);
        }
    };

    public class SelectionHandler extends GVREventListeners.TouchEvents
    {
        private GVRSceneObject mSelectionLight = null;

        public SelectionHandler(GVRContext ctx)
        {
            GVRPointLight light = new GVRPointLight(ctx);
            mSelectionLight = new GVRSceneObject(mContext);
            light.setAmbientIntensity(0, 0, 0, 1);
            light.setDiffuseIntensity(0.7f, 0.3f, 0.3f, 1);
            light.setSpecularIntensity(0.7f, 0.3f, 0.3f, 1);
            mSelectionLight.getTransform().setPositionY(1);
            mSelectionLight.attachComponent(light);
        }

        public void onEnter(GVRSceneObject sceneObj, GVRPicker.GVRPickedObject pickInfo)
        {
            GVRSceneObject.BoundingVolume bv = sceneObj.getBoundingVolume();
            GVRSceneObject parent = mSelectionLight.getParent();

            mSelectionLight.getTransform().setPositionZ(bv.radius);
            if (parent == sceneObj)
            {
                GVRLight light = mSelectionLight.getLight();
                light.setEnable(true);
                return;
            }
            if (parent != null)
            {
                parent.removeChildObject(mSelectionLight);
            }
            sceneObj.addChildObject(mSelectionLight);
        }

        public void onExit(GVRSceneObject sceneObj, GVRPicker.GVRPickedObject pickInfo)
        {
            GVRLight light = mSelectionLight.getLight();
            light.setEnable(false);
        }
    };

    public class TouchHandler extends GVREventListeners.TouchEvents
    {
        @Override
        public void onTouchEnd(GVRSceneObject sceneObj, GVRPicker.GVRPickedObject pickInfo)
        {
            if (mAvatar == null)
            {
                return;
            }
            GVRHitResult hit = mMixedReality.hitTest(pickInfo);

            if (hit == null)
            {
                return;
            }
            GVRSceneObject avatarModel = mAvatar.getModel();
            GVRSceneObject avatarAnchor = avatarModel.getParent();
            GVRAnchor   anchor = null;
            float[]     pose = hit.getPose();

            if (!mAvatar.isRunning())
            {
                mAvatar.startAll(GVRRepeatMode.REPEATED);
            }
            if (avatarAnchor != null)
            {
                anchor = (GVRAnchor) avatarAnchor.getComponent(GVRAnchor.getComponentType());
                mMixedReality.updateAnchorPose(anchor, pose);
            }
            else
            {
                avatarAnchor = new GVRSceneObject(mContext);
                avatarAnchor.addChildObject(avatarModel);
                anchor = mMixedReality.createAnchor(pose, avatarAnchor);
                avatarAnchor.attachComponent(new GVRBoxCollider(mContext));
                avatarAnchor.attachComponent(anchor);
                mScene.addSceneObject(avatarAnchor);
                avatarAnchor.getEventReceiver().addListener(mSelector);
            }
        }
    };



}