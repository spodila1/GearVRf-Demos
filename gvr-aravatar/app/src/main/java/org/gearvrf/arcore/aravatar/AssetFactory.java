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

import android.graphics.Color;
import android.util.Log;

import org.gearvrf.GVRAndroidResource;
import org.gearvrf.GVRContext;
import org.gearvrf.GVRDirectLight;
import org.gearvrf.GVRLight;
import org.gearvrf.GVRMaterial;
import org.gearvrf.GVRMesh;
import org.gearvrf.GVRPicker;
import org.gearvrf.GVRRenderData;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.ITouchEvents;
import org.gearvrf.animation.GVRAnimation;
import org.gearvrf.animation.GVRAnimator;
import org.gearvrf.animation.GVRAvatar;
import org.gearvrf.animation.GVRRepeatMode;
import org.gearvrf.io.GVRCursorController;
import org.gearvrf.io.GVRInputManager;
import org.joml.Vector4f;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;


public class AssetFactory
{
    private final String[] mAvatarNames = { "YBot", "Cat", "Eva" };
    private final String[] mAvatarAnimations =
            {
                    "YBot/Ybot_SambaDancing.bvh;Zombie_Stand_Up.bvh",
                    "Cat/cat_animation1.bvh;Cat/cat_animation2.bvh",
                    "Eva/eva_animation1.bvh;Eva/eva_animation2.bvh"
            };
    private static final String TAG = "AVATAR";
    private final String[] YBOT = new String[] { "YBot/YBot.fbx", "YBot/bonemap.txt", "YBot/Football_Hike.bvh", "YBot/Zombie_Stand_Up.bvh" };

    private final String[] EVA = { "Eva/Eva.dae", "Eva/bonemap.txt", "Eva/bvhExport_RUN.bvh", "Eva/eva_animation2.bvh" };

    private final String[] CAT = { "Cat/Cat.fbx", "Cat/bonemap.txt", "Cat/defaultAnim_SitDown.bvh", "Cat/defaultAnim_StandUp.bvh", "Cat/defaultAnim_Walk.bvh" };

    private final String[] HLMODEL = new String[] { "/sdcard/hololab.ply" };

    private final List<String[]> mAvatarFiles = new ArrayList<String[]>();
    private final List<GVRAvatar> mAvatars = new ArrayList<GVRAvatar>();
    private GVRSceneObject mCursor;
    private Vector4f[] mColors;
    private int mPlaneIndex = 0;
    private int mAvatarIndex = -1;
    private List<GVRAvatar> mAvatarList;
    private GVRContext mContext;
    private int mNumAnimsLoaded = 0;
    private String mBoneMap = null;

    AssetFactory(GVRContext ctx)
    {
        mContext = ctx;
        mAvatarFiles.add(0, EVA);
        mAvatarFiles.add(1, YBOT);
        mAvatarFiles.add(2, CAT);
        mAvatars.add(0, new GVRAvatar(ctx, "EVA"));
        mAvatars.add(1, new GVRAvatar(ctx, "YBOT"));
        mAvatars.add(2, new GVRAvatar(ctx, "CAT"));
        selectAvatar("EVA");
        mAvatarList = new ArrayList<>();
        mColors = new Vector4f[]
                {
                        new Vector4f(1, 0, 0, 0.2f),
                        new Vector4f(0, 1, 0, 0.2f),
                        new Vector4f(0, 0, 1, 0.2f),
                        new Vector4f(1, 0, 1, 0.2f),
                        new Vector4f(0, 1, 1, 0.2f),
                        new Vector4f(1, 1, 0, 0.2f),
                        new Vector4f(1, 1, 1, 0.2f),

                        new Vector4f(1, 0, 0.5f, 0.2f),
                        new Vector4f(0, 0.5f, 0, 0.2f),
                        new Vector4f(0, 0, 0.5f, 0.2f),
                        new Vector4f(1, 0, 0.5f, 0.2f),
                        new Vector4f(0, 1, 0.5f, 0.2f),
                        new Vector4f( 1, 0.5f, 0,0.2f),
                        new Vector4f( 1, 0.5f, 1,0.2f),

                        new Vector4f(0.5f, 0, 1, 0.2f),
                        new Vector4f(0.5f, 0, 1, 0.2f),
                        new Vector4f(0, 0.5f, 1, 0.2f),
                        new Vector4f( 0.5f, 1, 0,0.2f),
                        new Vector4f( 0.5f, 1, 1,0.2f),
                        new Vector4f( 1, 1, 0.5f, 0.2f),
                        new Vector4f( 1, 0.5f, 0.5f, 0.2f),
                        new Vector4f( 0.5f, 0.5f, 1, 0.2f),
                        new Vector4f( 0.5f, 1, 0.5f, 0.2f),
                };
    }
    public GVRAvatar selectAvatar(String name)
    {
        for (int i = 0; i < mAvatars.size(); ++i)
        {
            GVRAvatar avatar = mAvatars.get(i);
            if (name.equals(avatar.getName()))
            {
                if (mAvatarIndex == i)
                {
                    return avatar;
                }
                unselectAvatar();
                mAvatarIndex = i;
                mNumAnimsLoaded = avatar.getAnimationCount();
                if ((avatar.getSkeleton() == null) &&
                        (mAvatarListener != null))
                {
                    avatar.getEventReceiver().addListener(mAvatarListener);
                }

                if (mNumAnimsLoaded == 0)
                {
                    String mapFile = getMapFile();
                    if (mapFile != null)
                    {
                        mBoneMap = readFile(mContext,mapFile);
                    }
                    else
                    {
                        mBoneMap = null;
                    }
                }
                return avatar;
            }
        }
        return null;
    }
    private void unselectAvatar()
    {
        if (mAvatarIndex >= 0)
        {
            GVRAvatar avatar = getAvatar();
            avatar.stop();
            mNumAnimsLoaded = 0;
            mBoneMap = null;
        }
    }
    public boolean loadModel()
    {
        GVRAndroidResource res = null;
        GVRAvatar avatar = getAvatar();

        try
        {
            res = new GVRAndroidResource(mContext, getModelFile());
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            return false;
        }
        avatar.loadModel(res);
        return true;
    }
    public String getModelFile()
    {
        return mAvatarFiles.get(mAvatarIndex)[0];
    }

    public GVRAvatar getAvatar()
    {
        return mAvatars.get(mAvatarIndex);
    }
    public String getMapFile()
    {
        String[] files = mAvatarFiles.get(mAvatarIndex);
        if (files.length < 2)
        {
            return null;
        }
        return files[1];
    }
    public GVRSceneObject createPlane(GVRContext gvrContext, float scale)
    {
        GVRSceneObject plane = new GVRSceneObject(gvrContext);
        GVRMesh mesh = GVRMesh.createQuad(gvrContext,
                "float3 a_position", 1.0f, 1.0f);
        GVRMaterial mat = new GVRMaterial(gvrContext, GVRMaterial.GVRShaderType.Phong.ID);
        GVRSceneObject polygonObject = new GVRSceneObject(gvrContext, mesh, mat);
        Vector4f color = mColors[mPlaneIndex % mColors.length];

        plane.setName("Plane" + mPlaneIndex);
        polygonObject.setName("PlaneGeometry" + mPlaneIndex);
        mPlaneIndex++;
        mat.setDiffuseColor(color.x, color.y, color.x, color.w);
        polygonObject.getRenderData().setAlphaBlend(true);
        polygonObject.getRenderData().disableLight();
        polygonObject.getTransform().setRotationByAxis(-90, 1, 0, 0);
        polygonObject.getTransform().setScale(scale, scale, scale);
        plane.addChildObject(polygonObject);
        return plane;
    }

    public GVRDirectLight makeSceneLight(GVRContext ctx)
    {
        GVRSceneObject lightOwner = new GVRSceneObject(ctx);
        GVRDirectLight light = new GVRDirectLight(ctx);

        lightOwner.setName("SceneLight");
        light.setAmbientIntensity(0.2f, 0.2f, 0.2f, 1);
        light.setDiffuseIntensity(0.2f, 0.2f, 0.2f, 1);
        light.setSpecularIntensity(0.2f, 0.2f, 0.2f, 1);
        lightOwner.attachComponent(light);
        return light;
    }

    private GVRAvatar findAvatar(String name)
    {
        for (GVRAvatar av: mAvatarList)
        {
            if (av.getName().equals(name))
            {
                return av;
            }
        }
        return null;
    }

    private String[] findAnimations(String name)
    {
        int i = 0;
        for (GVRAvatar av : mAvatarList)
        {
            if (av.getName().equals(name))
            {
                String anims = mAvatarAnimations[2];
                return anims.split(";");
            }
            ++i;
        }
        return null;
    }
    public boolean loadNextAnimation()
    {
        String animFile = getAnimFile(mNumAnimsLoaded);
        if ((animFile == null) || (mBoneMap == null))
        {
            return false;
        }
        try
        {
            GVRAndroidResource res = new GVRAndroidResource(mContext, animFile);
            ++mNumAnimsLoaded;
            getAvatar().loadAnimation(res, mBoneMap);
            return true;
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            Log.e(TAG, "Animation could not be loaded from " + animFile);
            return false;
        }
    }
    public String getAnimFile(int animIndex)
    {
        String[] files = mAvatarFiles.get(mAvatarIndex);
        if (animIndex + 2 > files.length)
        {
            return null;
        }
        return files[2 + animIndex];
    }


    public GVRAvatar loadAvatar(GVRContext ctx, String avatarName)
    {
        GVRAvatar avatar = findAvatar(avatarName);
        String avatarPath = avatarName + "/" + avatarName + ".dae";
        if (avatar != null)
        {
            return avatar;
        }
        avatar = new GVRAvatar(ctx, avatarName);
        avatar.getEventReceiver().addListener(mAvatarListener);
        try
        {
            avatar.loadModel(new GVRAndroidResource(ctx, avatarPath));
            return avatar;
        }
        catch (IOException e)
        {
            e.printStackTrace();
            Log.e("ARAVATAR", "Avatar could not be loaded from " + avatarPath);
            return null;
        }
    }
/*
    public boolean loadAnimations(GVRAvatar avatar)
    {
        String avatarName = avatar.getName();
        String[] animpaths = findAnimations(avatarName);
        GVRContext ctx = avatar.getGVRContext();
        String boneMap = null;

        if (animpaths == null)
        {
            return false;
        }
        try
        {
            boneMap = readFile(ctx, avatarName + "/bonemap.txt");
        }
        catch (IOException e)
        {
            e.printStackTrace();
            Log.e("ARAVATAR", "Avatar bone mape could not be loaded from " + avatarName + "/bonemap.txt");
            return false;
        }
        for (String path : animpaths)
        {
            try
            {
                GVRAndroidResource res = new GVRAndroidResource(ctx, path);
                avatar.loadAnimation(res, boneMap);
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
                Log.e("ARAVATAR", "Animation could not be loaded from " + path);
                return false;
            }
        }
        return true;
    }*/

    private String readFile(GVRContext ctx, String filePath)
    {
        try
        {
            GVRAndroidResource res = new GVRAndroidResource(ctx, filePath);
            InputStream stream = res.getStream();
            byte[] bytes = new byte[stream.available()];
            stream.read(bytes);
            String s = new String(bytes);
            return s;
        }
        catch (IOException ex)
        {
            return null;
        }
    }

    public void initCursorController(GVRContext gvrContext, final ITouchEvents handler)
    {
        final int cursorDepth = 100;
        GVRInputManager inputManager = gvrContext.getInputManager();
        mCursor = new GVRSceneObject(gvrContext,
                gvrContext.createQuad(0.2f * cursorDepth,
                        0.2f * cursorDepth),
                gvrContext.getAssetLoader().loadTexture(new GVRAndroidResource(gvrContext,
                        R.raw.cursor)));
        mCursor.getRenderData().setDepthTest(false);
        mCursor.getRenderData().setRenderingOrder(GVRRenderData.GVRRenderingOrder.OVERLAY);
        final EnumSet<GVRPicker.EventOptions> eventOptions = EnumSet.of(
                GVRPicker.EventOptions.SEND_TOUCH_EVENTS,
                GVRPicker.EventOptions.SEND_TO_LISTENERS,
                GVRPicker.EventOptions.SEND_TO_HIT_OBJECT);
        inputManager.selectController(new GVRInputManager.ICursorControllerSelectListener()
        {
            public void onCursorControllerSelected(GVRCursorController newController, GVRCursorController oldController)
            {
                if (oldController != null)
                {
                    oldController.removePickEventListener(handler);
                }
                newController.setCursor(mCursor);
                newController.addPickEventListener(handler);
                mCursor.getRenderData().disableLight();
                newController.setCursorDepth(cursorDepth);
                newController.setCursorControl(GVRCursorController.CursorControl.PROJECT_CURSOR_ON_SURFACE);
                newController.getPicker().setEventOptions(eventOptions);
            }
        });
    }

    public  GVRAvatar.IAvatarEvents mAvatarListener = new GVRAvatar.IAvatarEvents()
    {
        @Override
        public void onAvatarLoaded(final GVRAvatar avatar, final GVRSceneObject avatarRoot, String filePath, String errors)
        {
            GVRSceneObject.BoundingVolume bv = avatarRoot.getBoundingVolume();
            float scale = 0.3f / bv.radius;
            avatarRoot.getTransform().setScale(scale, scale, scale);
            mAvatarList.add(avatar);
            loadNextAnimation();
        }

        @Override
        public void onAnimationLoaded(GVRAvatar avatar, GVRAnimator animation, String filePath, String errors)
        {
            if (animation != null)
            {
                animation.setRepeatMode(GVRRepeatMode.ONCE);
                animation.setSpeed(1f);
            }
            loadNextAnimation();
        }

        public void onModelLoaded(GVRAvatar avatar, GVRSceneObject avatarRoot, String filePath, String errors) { }

        public void onAnimationFinished(GVRAvatar avatar, GVRAnimator animator, GVRAnimation animation) { }

        public void onAnimationStarted(GVRAvatar avatar, GVRAnimator animator) { }
    };
}