package org.gearvrf.arpet.service.share;

import android.graphics.Bitmap;
import android.opengl.Matrix;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.util.Log;

import org.gearvrf.GVRPicker;
import org.gearvrf.GVRScene;
import org.gearvrf.GVRSceneObject;
import org.gearvrf.arpet.PetContext;
import org.gearvrf.arpet.constant.ArPetObjectType;
import org.gearvrf.arpet.service.IMessageService;
import org.gearvrf.arpet.service.MessageCallback;
import org.gearvrf.arpet.service.MessageException;
import org.gearvrf.arpet.service.MessageService;
import org.gearvrf.arpet.service.SimpleMessageReceiver;
import org.gearvrf.mixedreality.GVRAnchor;
import org.gearvrf.mixedreality.GVRAugmentedImage;
import org.gearvrf.mixedreality.GVRHitResult;
import org.gearvrf.mixedreality.GVRLightEstimate;
import org.gearvrf.mixedreality.GVRMixedReality;
import org.gearvrf.mixedreality.GVRPlane;
import org.gearvrf.mixedreality.IAnchorEventsListener;
import org.gearvrf.mixedreality.IAugmentedImageEventsListener;
import org.gearvrf.mixedreality.ICloudAnchorListener;
import org.gearvrf.mixedreality.IMRCommon;
import org.gearvrf.mixedreality.IPlaneEventsListener;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

public class SharedMixedReality implements IMRCommon {

    private static final String TAG = SharedMixedReality.class.getSimpleName();

    public static final int OFF = 0;
    public static final int HOST = 1;
    public static final int GUEST = 2;

    private final IMRCommon mMixedReality;
    private final PetContext mPetContext;
    private final List<SharedSceneObject> mSharedSceneObjects;
    private final IMessageService mMessageService;

    @Mode
    private int mMode = OFF;
    GVRAnchor mSharedAnchor = null;
    private float[] mSpaceMatrix = new float[16];

    @IntDef({OFF, HOST, GUEST})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {
    }

    public SharedMixedReality(PetContext petContext) {
        mMixedReality = new GVRMixedReality(petContext.getGVRContext(), true,
                petContext.getMainScene());
        mPetContext = petContext;
        mSharedSceneObjects = new ArrayList<>();
        mMessageService = MessageService.getInstance();
        mMessageService.addMessageReceiver(new LocalMessageReceiver(TAG));
        Matrix.setIdentityM(mSpaceMatrix, 0);
    }

    @Override
    public void resume() {
        mMixedReality.resume();
    }

    @Override
    public void pause() {
        mMixedReality.pause();
    }

    /**
     * Starts the sharing mode
     *
     * @param mode {@link SharedMixedReality#HOST} or {@link SharedMixedReality#GUEST}
     */
    public void startSharing(GVRAnchor sharedAnchor, @Mode int mode) {
        if (mMode != OFF) {
            return;
        }

        mSharedAnchor = sharedAnchor;

        mMode = mode;

        if (mode == HOST) {
            mPetContext.runOnPetThread(mSharingLoop);
        } else {
            startGuest();
        }
    }

    public void stopSharing() {
        if (mMode == GUEST) {
            stopGuest();
        }
        mMode = OFF;
    }

    public GVRAnchor getSharedAnchor() {
        return mSharedAnchor;
    }

    private synchronized void startGuest() {
        for (SharedSceneObject shared : mSharedSceneObjects) {
            initAsGuest(shared);
        }
    }

    private synchronized void initAsGuest(SharedSceneObject shared) {
        shared.parent = shared.object.getParent();
        if (shared.parent != null) {
            shared.parent.removeChildObject(shared.object);
            mPetContext.getMainScene().addSceneObject(shared.object);
        }
    }

    private synchronized void stopGuest() {
        for (SharedSceneObject shared : mSharedSceneObjects) {
            if (shared.parent != null) {
                mPetContext.getMainScene().removeSceneObject(shared.object);
                shared.parent.addChildObject(shared.object);
            }
        }
    }

    public synchronized void registerSharedObject(GVRSceneObject object, @ArPetObjectType String type) {
        SharedSceneObject shared = new SharedSceneObject(type, object);
        if (mMode == GUEST) {
            initAsGuest(shared);
        }
        mSharedSceneObjects.add(shared);
    }

    public synchronized void unregisterSharedObject(GVRSceneObject object) {
        for (SharedSceneObject shared : mSharedSceneObjects) {
            if (shared.object == object)
                mSharedSceneObjects.remove(shared);
        }
    }

    @Override
    public GVRSceneObject getPassThroughObject() {
        return mMixedReality.getPassThroughObject();
    }

    @Override
    public void registerPlaneListener(IPlaneEventsListener listener) {
        mMixedReality.registerPlaneListener(listener);
    }

    @Override
    public void unregisterPlaneListener(IPlaneEventsListener iPlaneEventsListener) {
        mMixedReality.unregisterPlaneListener(iPlaneEventsListener);
    }

    @Override
    public void registerAnchorListener(IAnchorEventsListener listener) {
        mMixedReality.registerAnchorListener(listener);
    }

    @Override
    public void registerAugmentedImageListener(IAugmentedImageEventsListener listener) {
        mMixedReality.registerAugmentedImageListener(listener);
    }

    @Override
    public ArrayList<GVRPlane> getAllPlanes() {
        return mMixedReality.getAllPlanes();
    }

    @Override
    public GVRAnchor createAnchor(float[] pose) {
        return mMixedReality.createAnchor(pose);
    }

    @Override
    public void updateAnchorPose(GVRAnchor gvrAnchor, float[] pose) {
        mMixedReality.updateAnchorPose(gvrAnchor, pose);
    }

    @Override
    public void removeAnchor(GVRAnchor gvrAnchor) {
        mMixedReality.removeAnchor(gvrAnchor);
    }

    @Override
    public void hostAnchor(GVRAnchor gvrAnchor, ICloudAnchorListener listener) {
        mMixedReality.hostAnchor(gvrAnchor, listener);
    }

    @Override
    public void resolveCloudAnchor(String anchorId, ICloudAnchorListener listener) {
        mMixedReality.resolveCloudAnchor(anchorId, listener);
    }

    @Override
    public void setEnableCloudAnchor(boolean enableCloudAnchor) {
        mMixedReality.setEnableCloudAnchor(enableCloudAnchor);
    }

    @Override
    public GVRHitResult hitTest(GVRSceneObject gvrSceneObject, GVRPicker.GVRPickedObject gvrPickedObject) {
        return mMixedReality.hitTest(gvrSceneObject, gvrPickedObject);
    }

    @Override
    public GVRHitResult hitTest(GVRSceneObject gvrSceneObject, float x, float y) {
        return mMixedReality.hitTest(gvrSceneObject, x, y);
    }

    @Override
    public GVRLightEstimate getLightEstimate() {
        return mMixedReality.getLightEstimate();
    }

    @Override
    public void setAugmentedImage(Bitmap bitmap) {
        mMixedReality.setAugmentedImage(bitmap);
    }

    @Override
    public void setAugmentedImages(ArrayList<Bitmap> arrayList) {
        mMixedReality.setAugmentedImages(arrayList);
    }

    @Override
    public ArrayList<GVRAugmentedImage> getAllAugmentedImages() {
        return mMixedReality.getAllAugmentedImages();
    }

    @Override
    public float[] makeInterpolated(float[] poseA, float[] poseB, float t) {
        return mMixedReality.makeInterpolated(poseA, poseB, t);
    }

    @Mode
    public int getMode() {
        return mMode;
    }

    private synchronized void sendSharedSceneObjects() {
        Matrix.invertM(mSpaceMatrix, 0,
                mSharedAnchor.getTransform().getModelMatrix(), 0);

        List<SharedObjectPose> poses = new ArrayList<>();

        for (SharedSceneObject shared : mSharedSceneObjects) {
            float[] result = new float[16];
            Matrix.multiplyMM(result, 0, mSpaceMatrix, 0,
                    shared.object.getTransform().getModelMatrix(), 0);
            poses.add(new SharedObjectPose(shared.type, result));
        }

        mMessageService.updatePoses(poses.toArray(new SharedObjectPose[0]), new MessageCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.d(TAG, "Success updating positions for " + poses);
            }

            @Override
            public void onFailure(Exception error) {
                Log.d(TAG, "Failed updating positions for "
                        + poses + ". Error: " + error.getMessage());
            }
        });
    }

    private synchronized void onUpdatePosesReceived(SharedObjectPose[] poses) {
        mSpaceMatrix = mSharedAnchor.getTransform().getModelMatrix();

        for (SharedObjectPose pose : poses) {
            for (SharedSceneObject shared : mSharedSceneObjects) {
                if (shared.type.equals(pose.getObjectType())) {
                    float[] result = new float[16];
                    Matrix.multiplyMM(result, 0, mSpaceMatrix, 0, pose.getModelMatrix(), 0);
                    shared.object.getTransform().setModelMatrix(result);
                    break;
                }
            }
        }
    }

    private Runnable mSharingLoop = new Runnable() {

        final int LOOP_TIME = 300;

        @Override
        public void run() {
            if (mMode != OFF) {
                sendSharedSceneObjects();
                mPetContext.runDelayedOnPetThread(this, LOOP_TIME);
            }
        }
    };

    private static class SharedSceneObject {

        @ArPetObjectType
        String type;

        // Shared object
        GVRSceneObject object;
        // Parent of shared object.
        GVRSceneObject parent;

        SharedSceneObject(String type, GVRSceneObject object) {
            this.type = type;
            this.object = object;
        }

        @Override
        public String toString() {
            return "SharedSceneObject{" +
                    ", type='" + type + '\'' +
                    '}';
        }
    }

    private class LocalMessageReceiver extends SimpleMessageReceiver {

        public LocalMessageReceiver(@NonNull String name) {
            super(name);
        }

        @Override
        public void onReceiveUpdatePoses(SharedObjectPose[] poses) throws MessageException {
            try {
                onUpdatePosesReceived(poses);
            } catch (Throwable t) {
                throw new MessageException("Error updating objects position", t);
            }
        }
    }
}
