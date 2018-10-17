/*
 * Copyright 2015 Samsung Electronics Co., LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.gearvrf.arpet.mode;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import org.gearvrf.GVRCameraRig;
import org.gearvrf.arpet.AnchoredObject;
import org.gearvrf.arpet.PetContext;
import org.gearvrf.arpet.character.CharacterView;
import org.gearvrf.arpet.common.Task;
import org.gearvrf.arpet.common.TaskException;
import org.gearvrf.arpet.connection.socket.ConnectionMode;
import org.gearvrf.arpet.constant.ApiConstants;
import org.gearvrf.arpet.constant.ArPetObjectType;
import org.gearvrf.arpet.manager.cloud.anchor.CloudAnchor;
import org.gearvrf.arpet.manager.cloud.anchor.CloudAnchorException;
import org.gearvrf.arpet.manager.cloud.anchor.CloudAnchorManager;
import org.gearvrf.arpet.manager.cloud.anchor.OnCloudAnchorManagerListener;
import org.gearvrf.arpet.manager.cloud.anchor.ResolvedCloudAnchor;
import org.gearvrf.arpet.manager.connection.IPetConnectionManager;
import org.gearvrf.arpet.manager.connection.PetConnectionEvent;
import org.gearvrf.arpet.manager.connection.PetConnectionEventHandler;
import org.gearvrf.arpet.manager.connection.PetConnectionEventType;
import org.gearvrf.arpet.manager.connection.PetConnectionManager;
import org.gearvrf.arpet.service.IMessageService;
import org.gearvrf.arpet.service.MessageCallback;
import org.gearvrf.arpet.service.MessageException;
import org.gearvrf.arpet.service.MessageService;
import org.gearvrf.arpet.service.SimpleMessageReceiver;
import org.gearvrf.arpet.service.data.ViewCommand;
import org.gearvrf.arpet.service.share.SharedMixedReality;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.gearvrf.arpet.mode.ShareAnchorView.UserType.GUEST;

public class ShareAnchorMode extends BasePetMode {
    private final String TAG = getClass().getSimpleName();

    private static final int DEFAULT_SERVER_LISTENING_TIMEOUT = ApiConstants.DISCOVERABLE_DURATION * 1000; // time in ms
    private static final int DEFAULT_GUEST_TIMEOUT = 10000;  // 10s for waiting to connect to the host
    private final int DEFAULT_SCREEN_TIMEOUT = 5000;  // 5s to change between screens

    private IPetConnectionManager mConnectionManager;
    private final Handler mHandler = new Handler();
    private ShareAnchorView mShareAnchorView;
    private final List<AnchoredObject> mAnchoredObjects;
    private CloudAnchorManager mCloudAnchorManager;
    private IMessageService mMessageService;
    private OnBackToHudModeListener mBackToHudModeListener;
    private SharedMixedReality mSharedMixedReality;

    public ShareAnchorMode(PetContext petContext, List<AnchoredObject> anchoredObjects, OnBackToHudModeListener listener) {
        super(petContext, new ShareAnchorView(petContext));
        mConnectionManager = PetConnectionManager.getInstance();
        mConnectionManager.addEventHandler(new AppConnectionMessageHandler());
        mShareAnchorView = (ShareAnchorView) mModeScene;
        mShareAnchorView.setListenerShareAnchorMode(new HandlerActionsShareAnchorMode());
        mAnchoredObjects = anchoredObjects;
        mCloudAnchorManager = new CloudAnchorManager(petContext, new CloudAnchorManagerReadyListener());
        mBackToHudModeListener = listener;
        mMessageService = MessageService.getInstance();
        mMessageService.addMessageReceiver(new MessageReceiver());
        mSharedMixedReality = (SharedMixedReality) petContext.getMixedReality();
    }

    @Override
    protected void onEnter() {

    }

    @Override
    protected void onExit() {

    }

    @Override
    protected void onHandleOrientation(GVRCameraRig cameraRig) {

    }

    public class HandlerActionsShareAnchorMode implements ShareAnchorListener {

        @Override
        public void OnHost() {
            mConnectionManager.startUsersInvitation();
        }

        @Override
        public void OnGuest() {
            // Disable the planes detection
            mPetContext.unregisterPlaneListener();

            // Start to accept invitation from the host
            mConnectionManager.acceptInvitation();

            // Change the views
            mShareAnchorView.modeGuest();
            mShareAnchorView.getProgressHandler().setDuration(DEFAULT_GUEST_TIMEOUT);
            mShareAnchorView.getProgressHandler().start();
        }

        @Override
        public void OnBackShareAnchor() {
            mBackToHudModeListener.OnBackToHud();
        }

        @Override
        public void OnCancel() {
            mBackToHudModeListener.OnBackToHud();
        }

        @Override
        public void OnTry() {
            if (mShareAnchorView.getUserType() == GUEST) {
                OnGuest();
            } else {
                OnHost();
            }
        }

        @Override
        public void OnDisconnectScreen() {
            if (mShareAnchorView.getUserType() == GUEST) {
                mShareAnchorView.disconnectScreenGuest();
            } else {
                mShareAnchorView.disconnectScreenHost();
            }

        }

        @Override
        public void OnConnectedScreen() {
            mShareAnchorView.modeView();
        }
    }

    private void showWaitingForScreen() {
        mShareAnchorView.modeHost();
        mShareAnchorView.getProgressHandler().setDuration(DEFAULT_SERVER_LISTENING_TIMEOUT);
        mShareAnchorView.getProgressHandler().start();
        mHandler.postDelayed(this::OnWaitingForConnection, DEFAULT_SERVER_LISTENING_TIMEOUT);
    }

    private void showInviteAcceptedScreen() {
        if (mConnectionManager.getConnectionMode() == ConnectionMode.SERVER) {
            Log.d(TAG, "host");
            mShareAnchorView.inviteAcceptedHost(mConnectionManager.getTotalConnected());
            mHandler.postDelayed(() -> {
                mShareAnchorView.centerPetView();
                mHandler.postDelayed(() -> showMoveAround(), DEFAULT_SCREEN_TIMEOUT);
            }, DEFAULT_SCREEN_TIMEOUT);

            for (AnchoredObject object : mAnchoredObjects) {
                mCloudAnchorManager.hostAnchor(object);
            }
        } else {
            Log.d(TAG, "guest");
            mShareAnchorView.inviteAcceptedGuest();
            mHandler.postDelayed(() -> showWaitingScreen(), DEFAULT_SCREEN_TIMEOUT);
        }
    }

    private void showSharedHost() {
        mPetContext.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mShareAnchorView.sharedHost();
            }
        });
    }

    private void OnWaitingForConnection() {
        Log.d(TAG, "OnWaitingForConnection: time up");
        mConnectionManager.stopUsersInvitation();
        if (mConnectionManager.getTotalConnected() > 0) {
            Log.d(TAG, "Total " + mConnectionManager.getTotalConnected());
            mShareAnchorView.inviteAcceptedGuest();
        }
    }

    private void showNotFound() {
        final String[] type = {""};
        mPetContext.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mShareAnchorView.getUserType() == GUEST) {
                    type[0] = "Host";
                    mShareAnchorView.notFound(type[0]);
                } else {
                    type[0] = "Guest";
                    mShareAnchorView.notFound(type[0]);
                }
            }
        });
    }

    private void showPairingError() {
        mPetContext.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mShareAnchorView.pairingError();
            }
        });
    }

    @SuppressLint("HandlerLeak")
    private class AppConnectionMessageHandler implements PetConnectionEventHandler {

        @SuppressLint("SwitchIntDef")
        @Override
        public void handleEvent(PetConnectionEvent message) {
            mPetContext.getActivity().runOnUiThread(() -> {
                @PetConnectionEventType
                int messageType = message.getType();
                switch (messageType) {
                    case PetConnectionEventType.CONNECTION_ESTABLISHED:
                        handleConnectionEstablished();
                        break;
                    case PetConnectionEventType.CONNECTION_NOT_FOUND:
                        showNotFound();
                        break;
                    case PetConnectionEventType.CONNECTION_ALL_LOST:
                        onSharingOff();
                        showToast("Connection lost");
                        break;
                    case PetConnectionEventType.CONNECTION_LISTENER_STARTED:
                        showToast("Ready to accept connections");
                        showWaitingForScreen();
                        break;
                    case PetConnectionEventType.ERROR_BLUETOOTH_NOT_ENABLED:
                        showToast("Bluetooth is disabled");
                        break;
                    case PetConnectionEventType.ERROR_DEVICE_NOT_DISCOVERABLE:
                        showToast("This device is not visible to other devices");
                        break;
                    default:
                        break;
                }
            });
        }
    }

    private void onSharingOff() {
        mSharedMixedReality.stopSharing();
    }

    private void handleConnectionEstablished() {
        switch (mConnectionManager.getConnectionMode()) {
            case ConnectionMode.CLIENT: {
                showInviteAcceptedScreen();
                break;
            }
            case ConnectionMode.SERVER: {
                showInviteAcceptedScreen();
                break;
            }
            case ConnectionMode.NONE:
            default:
                break;
        }
    }

    private void showToast(String text) {
        Log.d(TAG, "showToast: " + text);
        Toast.makeText(mPetContext.getActivity(), text, Toast.LENGTH_LONG).show();
    }

    private void showWaitingScreen() {
        mPetContext.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mShareAnchorView.waitingView();
            }
        });
    }

    private void showStayInPositionToPair() {
        mPetContext.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mShareAnchorView.toPairView();
            }
        });
    }

    private void showCenterPet() {
        mPetContext.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mShareAnchorView.centerPetView();
            }
        });
    }

    private void showMoveAround() {
        mPetContext.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mShareAnchorView.moveAroundView();
            }
        });
    }

    private void showModeShareAnchorView() {
        mPetContext.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mHandler.postDelayed(() -> showStatusModeView(), DEFAULT_SCREEN_TIMEOUT);
            }
        });
    }

    private void showStatusModeView() {
        mPetContext.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mShareAnchorView.modeView();
            }
        });
    }

    private void showLookingSidebySide() {
        mPetContext.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mShareAnchorView.lookingSidebySide();
            }
        });
    }


    private void sendCommandToShowModeShareAnchorView() {
        ViewCommand command = new ViewCommand(ViewCommand.SHOW_MODE_SHARE_ANCHOR_VIEW);
        mMessageService.sendViewCommand(command, new MessageCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                showModeShareAnchorView();
            }

            @Override
            public void onFailure(Exception error) {
                Log.e(TAG, "command to show 'paired view' has failed");
            }
        });
    }

    private void sendCommandLookingSideBySide() {
        ViewCommand command = new ViewCommand(ViewCommand.LOOKING_SIDE_BY_SIDE);
        mMessageService.sendViewCommand(command, new MessageCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.d(TAG, "command to show 'looking side by  view' was performed successfully");
            }

            @Override
            public void onFailure(Exception error) {
                Log.e(TAG, "command to show 'looking side by  view' has failed");
            }
        });
    }

    private void sendCommandSharedHost() {
        ViewCommand command = new ViewCommand(ViewCommand.SHARED_HOST);
        mMessageService.sendViewCommand(command, new MessageCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.d(TAG, "command to show 'shared host view' was performed successfully");
            }

            @Override
            public void onFailure(Exception error) {
                Log.e(TAG, "command to show 'shared host view' has failed");
            }
        });
    }

    private void shareScene(CloudAnchor[] anchors) {
        mMessageService.shareCloudAnchors(anchors, new MessageCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                showToast("Pet anchor successfully resolved on guest");
                // Inform the guests to change their view
                sendCommandToShowModeShareAnchorView();

                mAnchoredObjects.stream()
                        .filter(CharacterView.class::isInstance)
                        .findFirst()
                        .ifPresent(petView -> mSharedMixedReality.startSharing(
                                petView.getAnchor().getPose(), SharedMixedReality.HOST));
            }

            @Override
            public void onFailure(Exception error) {
                showToast("Error resolving pet anchor on guest: " + error.getMessage());
                showPairingError();
            }
        });
    }

    private class CloudAnchorManagerReadyListener implements OnCloudAnchorManagerListener {
        @Override
        public void onHostReady() {
            // Just inform the guests to change their view
            sendCommandSharedHost();
            sendCommandLookingSideBySide();
            // Change the host view
            showStayInPositionToPair();

            Log.d(TAG, "sending a list of CloudAnchor objects to the guests");
            ArrayList<CloudAnchor> cloudAnchors = mCloudAnchorManager.getCloudAnchors();
            int listSize = cloudAnchors.size();
            shareScene(cloudAnchors.toArray(new CloudAnchor[listSize]));
        }
    }

    private class MessageReceiver extends SimpleMessageReceiver {

        @Override
        public void onReceiveShareCloudAnchors(CloudAnchor[] cloudAnchors) throws MessageException {
            Log.d(TAG, "Sharing received: " + Arrays.toString(cloudAnchors));
            CloudAnchorResolverTask task = new CloudAnchorResolverTask(cloudAnchors);
            // This method gets locked and will return after loader finish
            Log.d(TAG, "Resolving anchors...");
            task.start();
            Log.d(TAG, "Task ended");
            // Lock released, now checks thread result
            if (task.getError() != null) {
                showToast(task.getError().getMessage());
                throw new MessageException(task.getError());
            } else {
                showToast("All anchors successfully resolved");
                // Start sharing using resolved pet pose as guest's world center
                ResolvedCloudAnchor cloudAnchor = task.getResolvedCloudAnchorByType(ArPetObjectType.PET);
                if (cloudAnchor != null) {
                    float[] petPose = cloudAnchor.getAnchor().getPose();
                    mSharedMixedReality.startSharing(petPose, SharedMixedReality.GUEST);
                }
            }
        }

        @Override
        public void onReceiveViewCommand(ViewCommand command) throws MessageException {
            try {
                Log.d(TAG, "View command received: " + command);
                switch (command.getType()) {
                    case ViewCommand.SHOW_MODE_SHARE_ANCHOR_VIEW:
                        showModeShareAnchorView();
                        break;
                    case ViewCommand.LOOKING_SIDE_BY_SIDE:
                        mHandler.postDelayed(() -> showLookingSidebySide(), DEFAULT_SCREEN_TIMEOUT);
                        break;
                    case ViewCommand.SHARED_HOST:
                        showSharedHost();
                        break;
                    default:
                        Log.d(TAG, "Unknown view command: " + command.getType());
                        break;
                }
            } catch (Throwable t) {
                throw new MessageException("Error processing view command", t);
            }
        }
    }

    private class CloudAnchorResolverTask extends Task {

        CloudAnchor[] mCloudAnchors;
        List<ResolvedCloudAnchor> mResolvedCloudAnchors;

        CloudAnchorResolverTask(CloudAnchor[] cloudAnchors) {
            this.mCloudAnchors = cloudAnchors;
        }

        @Override
        public void execute() {

            mCloudAnchorManager.resolveAnchors(mCloudAnchors, new CloudAnchorManager.OnResolveCallback() {
                @Override
                public void onAllResolved(List<ResolvedCloudAnchor> resolvedCloudAnchors) {
                    mResolvedCloudAnchors = new ArrayList<>(resolvedCloudAnchors);
                    notifyExecuted();
                }

                @Override
                public void onError(CloudAnchorException e) {
                    setError(new TaskException(e));
                    notifyExecuted();
                }
            });
        }

        ResolvedCloudAnchor getResolvedCloudAnchorByType(@ArPetObjectType String type) {
            return mResolvedCloudAnchors.stream()
                    .filter(a -> a.getObjectType().equals(type))
                    .findFirst()
                    .orElse(null);
        }
    }
}
