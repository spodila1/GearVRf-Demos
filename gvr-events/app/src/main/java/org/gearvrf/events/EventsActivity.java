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

package org.gearvrf.events;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.gearvrf.GVRApplication;
import org.gearvrf.GVRMain;

import java.util.ArrayList;
import java.util.List;

public class EventsActivity extends Activity {
    private GVRApplication application;
    private GVRMain main;
    private FrameLayout frameLayout;
    private TextView buttonTextView, keyTextView, listTextView;
    private Button button1, button2;
    private CheckBox checkBox;
    private String buttonPressed, listItemClicked;
    private ListView listView;

    private static final List<String> items = new ArrayList<String>(5);

    static {
        items.add("Note 4");
        items.add("GS 6");
        items.add("GS 6 Edge");
        items.add("Note 5");
        items.add("GS 6 Edge Plus");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        frameLayout = new FrameLayout(this);
        frameLayout.setBackgroundColor(Color.WHITE);

        main = new EventsMain(this, frameLayout);
        application = new GVRApplication(this, main, "gvr.xml");
        application.registerView(frameLayout);

        frameLayout.getLayoutParams().height = frameLayout.getLayoutParams().width = 700;
        View.inflate(this, R.layout.activity_main, frameLayout);

        button1 = frameLayout.findViewById(R.id.button1);
        button2 = frameLayout.findViewById(R.id.button2);
        checkBox = frameLayout.findViewById(R.id.checkBox);
        keyTextView = frameLayout.findViewById(R.id.keyTextView);
        buttonTextView = frameLayout.findViewById(R.id.buttonTextView);
        listTextView = frameLayout.findViewById(R.id.listTextView);
        listView = findViewById(R.id.listView);
        listView.setBackgroundColor(Color.LTGRAY);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.list_item, items);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(itemClickListener);
        button1.setOnClickListener(clickListener);
        button1.setOnHoverListener(buttonHoverListener);
        button2.setOnClickListener(clickListener);
        button2.setOnHoverListener(buttonHoverListener);
        checkBox.setOnClickListener(clickListener);
        buttonPressed = getResources().getString(R.string.buttonPressed);
        listItemClicked = getResources().getString(R.string.listClicked);
    }

    private OnClickListener clickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            String button = new String();
            switch (v.getId()) {
                case R.id.button1:
                    button = "1";
                    break;
                case R.id.button2:
                    button = "2";
                    break;
                case R.id.checkBox:
                    button = "Check Box";
                    break;
                default:
                    break;
            }

            buttonTextView
                    .setText(String.format("%s %s", buttonPressed, button));
        }
    };

    private OnItemClickListener itemClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> arg0, View arg1, int position,
                                long arg3) {
            listTextView.setText(String.format("%s %s", listItemClicked,
                    items.get(position)));
        }
    };

    private View.OnHoverListener buttonHoverListener = new View.OnHoverListener() {
        @Override
        public boolean onHover(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    ((Button) v).setTextColor(Color.WHITE);
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                    ((Button) v).setTextColor(Color.BLACK);
                    break;
                default:
                    break;
            }
            return false;
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        keyTextView.setText(String.format("Key Pressed: %s ",
                KeyEvent.keyCodeToString(keyCode)));
        return false;
    }

    @Override
    protected void onPause() {
        application.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        application.resume();
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        application.destroy();
        super.onDestroy();
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (application.dispatchGenericMotionEvent(event)) {
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (application.dispatchTouchEvent(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

}