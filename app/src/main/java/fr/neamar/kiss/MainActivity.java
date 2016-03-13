package fr.neamar.kiss;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.RotateAnimation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import java.util.ArrayList;

import fr.neamar.kiss.adapter.RecordAdapter;
import fr.neamar.kiss.pojo.Pojo;
import fr.neamar.kiss.result.Result;
import fr.neamar.kiss.searcher.ApplicationsSearcher;
import fr.neamar.kiss.searcher.HistorySearcher;
import fr.neamar.kiss.searcher.NullSearcher;
import fr.neamar.kiss.searcher.QueryInterface;
import fr.neamar.kiss.searcher.QuerySearcher;
import fr.neamar.kiss.searcher.Searcher;

public class MainActivity extends ListActivity implements QueryInterface {

    public static final String START_LOAD = "fr.neamar.summon.START_LOAD";
    public static final String LOAD_OVER = "fr.neamar.summon.LOAD_OVER";
    public static final String FULL_LOAD_OVER = "fr.neamar.summon.FULL_LOAD_OVER";

    /**
     * IDS for the favorites buttons
     */
    private final int[] favsIds = new int[]{R.id.favorite0, R.id.favorite1, R.id.favorite2, R.id.favorite3, R.id.favorite4};
    private final int[] favsCircleIds = new int[]{R.id.favcircle0, R.id.favcircle1, R.id.favcircle2, R.id.favcircle3, R.id.favcircle4};
    private final int[] favsLayoutIds = new int[]{R.id.fav_layout_0, R.id.fav_layout_1, R.id.fav_layout_2, R.id.fav_layout_3, R.id.fav_layout_4};

    /**
     * Number of favorites to retrieve.
     * We need to pad this number to account for removed items still in history
     */
    private final int tryToRetrieve = favsIds.length + 2;
    /**
     * InputType with spellecheck and swiping
     */
    private final int spellcheckEnabledType = InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
    /**
     * Adapter to display records
     */
    public RecordAdapter adapter;
    /**
     * Store user preferences
     */
    private SharedPreferences prefs;
    private BroadcastReceiver mReceiver;
    /**
     * View for the Search text
     */
    private EditText searchEditText;
    private final Runnable displayKeyboardRunnable = new Runnable() {
        @Override
        public void run() {
            showKeyboard();
        }
    };
    /**
     * Menu button
     */
    private View menuButton;
    /**
     * Kiss bar
     */
    private View kissBar;
    /**
     * Task launched on text change
     */
    private Searcher searcher;

    private ListView resultsListView;

    private LinearLayout main_empty_layout;
    private boolean isEmptyMenuVisible = false;
    private View favoritesIconExpandingView;

    /**
     * Called when the activity is first created.
     */
    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Initialize UI
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        String theme = prefs.getString("theme", "light");
        if (theme.equals("dark")) {
            setTheme(R.style.AppThemeDark);
        } else if (theme.equals("transparent")) {
            setTheme(R.style.AppThemeTransparent);
        } else if (theme.equals("semi-transparent")) {
            setTheme(R.style.AppThemeSemiTransparent);
        } else if (theme.equals("semi-transparent-dark")) {
            setTheme(R.style.AppThemeSemiTransparentDark);
        }

        super.onCreate(savedInstanceState);

        IntentFilter intentFilter = new IntentFilter(START_LOAD);
        IntentFilter intentFilterBis = new IntentFilter(LOAD_OVER);
        IntentFilter intentFilterTer = new IntentFilter(FULL_LOAD_OVER);
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equalsIgnoreCase(LOAD_OVER)) {
                    updateRecords(searchEditText.getText().toString());
                } else if (intent.getAction().equalsIgnoreCase(FULL_LOAD_OVER)) {
                    displayLoader(false);

                } else if (intent.getAction().equalsIgnoreCase(START_LOAD)) {
                    displayLoader(true);
                }
            }
        };

        this.registerReceiver(mReceiver, intentFilter);
        this.registerReceiver(mReceiver, intentFilterBis);
        this.registerReceiver(mReceiver, intentFilterTer);
        KissApplication.initDataHandler(this);

        // Initialize preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Lock launcher into portrait mode
        // Do it here (before initializing the view) to make the transition as smooth as possible
        if (prefs.getBoolean("force-portrait", true)) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }

        setContentView(R.layout.main);

        // Create adapter for records
        adapter = new RecordAdapter(this, this, R.layout.item_app, new ArrayList<Result>());
        setListAdapter(adapter);

        searchEditText = (EditText) findViewById(R.id.searchEditText);

        // Listen to changes
        searchEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                // Auto left-trim text.
                if (s.length() > 0 && s.charAt(0) == ' ')
                    s.delete(0, 1);
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateRecords(s.toString());
                displayClearOnInput();
            }
        });

        // On validate, launch first record
        searchEditText.setOnEditorActionListener(new OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                RecordAdapter adapter = ((RecordAdapter) getListView().getAdapter());

                adapter.onClick(adapter.getCount() - 1, null);

                return true;
            }
        });

        searchEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (prefs.getBoolean("history-hide", false) && prefs.getBoolean("history-onclick", false)) {
                    //show history only if no search text is added
                    if (((EditText) v).getText().toString().isEmpty()) {
                        searcher = new HistorySearcher(MainActivity.this);
                        searcher.execute();
                    }
                }
            }
        });

        kissBar = findViewById(R.id.main_kissbar);
        menuButton = findViewById(R.id.menuButton);
        registerForContextMenu(menuButton);

        favoritesIconExpandingView = findViewById(R.id.favorites_icon_expand);

        resultsListView = getListView();
//        resultsListView = (ListView) findViewById(R.id.list);
        main_empty_layout = (LinearLayout) findViewById(R.id.main_empty);

        getListView().setLongClickable(true);
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View v, int pos, long id) {
                ((RecordAdapter) parent.getAdapter()).onLongClick(pos, v);
                return true;
            }
        });

        // Enable swiping
        if (prefs.getBoolean("enable-spellcheck", false)) {
            searchEditText.setInputType(spellcheckEnabledType);
        }

        // Hide the "X" after the text field, instead displaying the menu button
        displayClearOnInput();

        // Apply effects depending on current Android version
        applyDesignTweaks();
    }

    /**
     * Apply some tweaks to the design, depending on the current SDK version
     */
    private void applyDesignTweaks() {
        final int[] tweakableIds = new int[]{
                R.id.menuButton,
                // Barely visible on the clearbutton, since it disappears instant. Can be seen on long click though
                R.id.clearButton,
                R.id.launcherButton,
                R.id.favorite0,
                R.id.favorite1,
                R.id.favorite2,
                R.id.favorite3,
                R.id.favorite4,
        };

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            TypedValue outValue = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);

            for (int id : tweakableIds) {
                findViewById(id).setBackgroundResource(outValue.resourceId);
            }

        } else if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            TypedValue outValue = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);

            for (int id : tweakableIds) {
                findViewById(id).setBackgroundResource(outValue.resourceId);
            }
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        adapter.onClick(position, v);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_settings, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return onOptionsItemSelected(item);
    }

    /**
     * Empty text field on resume and show keyboard
     */
    protected void onResume() {
        if (prefs.getBoolean("require-layout-update", false)) {
            // Restart current activity to refresh view, since some preferences
            // may require using a new UI
            prefs.edit().putBoolean("require-layout-update", false).commit();
            Intent i = new Intent(this, getClass());
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            finish();
            overridePendingTransition(0, 0);
            startActivity(i);
            overridePendingTransition(0, 0);
        }

        if (kissBar.getVisibility() != View.VISIBLE) {
            updateRecords(searchEditText.getText().toString());
            displayClearOnInput();
        } else {
            displayKissBar(false);
        }

        // Activity manifest specifies stateAlwaysHidden as windowSoftInputMode
        // so the keyboard will be hidden by default
        // we may want to display it if the setting is set
        if (prefs.getBoolean("display-keyboard", false)) {
            // Display keyboard
            showKeyboard();

            new Handler().postDelayed(displayKeyboardRunnable, 10);
            // For some weird reasons, keyboard may be hidden by the system
            // So we have to run this multiple time at different time
            // See https://github.com/Neamar/KISS/issues/119
            new Handler().postDelayed(displayKeyboardRunnable, 100);
            new Handler().postDelayed(displayKeyboardRunnable, 500);
        } else {
            // Not used (thanks windowSoftInputMode)
            // unless coming back from KISS settings
            hideKeyboard();
        }

        if (favoritesIconExpandingView.getVisibility() == View.VISIBLE) {
            favoritesIconExpandingView.setVisibility(View.GONE);
        }

        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (favoritesIconExpandingView.getVisibility() == View.VISIBLE) {
            favoritesIconExpandingView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // unregister our receiver
        this.unregisterReceiver(this.mReceiver);
        KissApplication.getCameraHandler().releaseCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        KissApplication.getCameraHandler().releaseCamera();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // Empty method,
        // This is called when the user press Home again while already browsing MainActivity
        // onResume() will be called right after, hiding the kissbar if any.
        // http://developer.android.com/reference/android/app/Activity.html#onNewIntent(android.content.Intent)
    }

    @Override
    public void onBackPressed() {
        // Is the kiss menu visible?
        if (menuButton.getVisibility() == View.VISIBLE) {
            displayKissBar(false);
        } else {
            // If no kissmenu, empty the search bar
            searchEditText.setText("");
        }

        // No call to super.onBackPressed, since this would quit the launcher.
    }

    @Override
    public boolean onKeyDown(int keycode, @NonNull KeyEvent e) {
        switch (keycode) {
            case KeyEvent.KEYCODE_MENU:
                // For user with a physical menu button, we still want to display *our* contextual menu
                menuButton.showContextMenu();
                return true;
        }

        return super.onKeyDown(keycode, e);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                return true;
            case R.id.wallpaper:
                hideKeyboard();
                Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
                startActivity(Intent.createChooser(intent, getString(R.string.menu_wallpaper)));
                return true;
            case R.id.preferences:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_settings, menu);

        return true;
    }

    /**
     * Display menu, on short or long press.
     *
     * @param menuButton "kebab" menu (3 dots)
     */
    public void onMenuButtonClicked(View menuButton) {
        // When the kiss bar is displayed, the button can still be clicked in a few areas (due to favorite margin)
        // To fix this, we discard any click event occurring when the kissbar is displayed
//        if (kissBar.getVisibility() != View.VISIBLE)
            menuButton.showContextMenu();
    }

    /**
     * Clear text content when touching the cross button
     */
    @SuppressWarnings("UnusedParameters")
    public void onClearButtonClicked(View clearButton) {
        searchEditText.setText("");
    }

    /**
     * Display KISS menu
     */
    public void onLauncherButtonClicked(View launcherButton) {
        // Display or hide the kiss bar, according to current view tag (showMenu / hideMenu).

//        displayKissBar(launcherButton.getTag().equals("showMenu"));

        boolean display;

        if (resultsListView.getVisibility() == View.VISIBLE) {
            display = false;
        } else {
            display = true;
        }

        displayKissBar(display);
    }

    public void onShowFavoritesButtonClicked(View launcherButton) {
        // Display or hide the kiss bar, according to current view tag (showMenu / hideMenu).

//        displayFavoritesBar(launcherButton.getTag().equals("showMenu"));

        boolean display;

        if (kissBar.getVisibility() == View.VISIBLE) {
            display = false;
        } else {
            display = true;
        }

        displayFavoritesBar(display);
    }

    public void onFavoriteButtonClicked(View favorite) {

        // Favorites handling
        Pojo pojo = KissApplication.getDataHandler(MainActivity.this).getFavorites(tryToRetrieve)
                .get(Integer.parseInt((String) favorite.getTag()));
        final Result result = Result.fromPojo(MainActivity.this, pojo);
        ImageView selectedFavorite = (ImageView) findViewById(favorite.getId());
        FrameLayout favoriteLayout = (FrameLayout) favorite.getParent();

        int width = getApplicationContext().getResources().getDisplayMetrics().widthPixels;
        int height = getApplicationContext().getResources().getDisplayMetrics().heightPixels;

        int location[] = {0, 0};
        favoriteLayout.getLocationInWindow(location);
        int cx = location[0];
        int cy = location[1];

//        int layoutRadius = Math.min(favoriteLayout.getWidth(), favoriteLayout.getHeight()) / 2;
        ImageView circle = (ImageView) findViewById(R.id.favcircle0);
        int layoutRadius = Math.min(circle.getWidth(), circle.getHeight()) / 2;
        int centerX = favoriteLayout.getWidth() / 2;
        int centerY = favoriteLayout.getHeight() / 2;

        int startX = cx + centerX;
        int startY = cy;

        int duration = 300; //Normally 300. Can set to 1000 to show off animation more

        int finalRadius = (int) Math.hypot(Math.abs(width - startX), Math.abs(height - startY));

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            final float favoriteElevation = favoriteLayout.getElevation();
//            favoriteLayout.setElevation(100);
            Animator anim = ViewAnimationUtils.createCircularReveal(favoritesIconExpandingView, startX, startY, layoutRadius, finalRadius);
            anim.setDuration(duration);

            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    kissBar.setVisibility(View.GONE);
//                    favoriteLayout.setElevation(favoriteElevation);
//                    searchEditText.setText("");
                }
            });
            favoritesIconExpandingView.setVisibility(View.VISIBLE);
            anim.start();
        } else {
            // No animation before Lollipop
            kissBar.setVisibility(View.GONE);
        }

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                result.fastLaunch(MainActivity.this);
            }
        }, KissApplication.TOUCH_DELAY);
    }

    private void displayClearOnInput() {
        final View clearButton = findViewById(R.id.clearButton);
        if (searchEditText.getText().length() > 0) {
            clearButton.setVisibility(View.VISIBLE);
            menuButton.setVisibility(View.INVISIBLE);
        } else {
            clearButton.setVisibility(View.INVISIBLE);
            menuButton.setVisibility(View.VISIBLE);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    private void displayLoader(Boolean display) {
        final View loaderBar = findViewById(R.id.loaderBar);
        final View launcherButton = findViewById(R.id.launcherButton);

        int animationDuration = getResources().getInteger(
                android.R.integer.config_longAnimTime);

        if (!display) {
            launcherButton.setVisibility(View.VISIBLE);

            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                // Animate transition from loader to launch button
                launcherButton.setAlpha(0);
                launcherButton.animate()
                        .alpha(1f)
                        .setDuration(animationDuration)
                        .setListener(null);
                loaderBar.animate()
                        .alpha(0f)
                        .setDuration(animationDuration)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                loaderBar.setVisibility(View.GONE);
                                loaderBar.setAlpha(1);
                            }
                        });
            } else {
                loaderBar.setVisibility(View.GONE);
            }
        } else {
            launcherButton.setVisibility(View.INVISIBLE);
            loaderBar.setVisibility(View.VISIBLE);
        }
    }

    private void displayKissBar(Boolean display) {
        final ImageView launcherButton = (ImageView) findViewById(R.id.launcherButton);

        // get the center for the clipping circle
//        int cx = (launcherButton.getLeft() + launcherButton.getRight()) / 2;
//        int cy = resultsListView.getHeight() - (launcherButton.getTop() + launcherButton.getBottom()) / 2;
//        int cx = (resultsListView.getLeft() + resultsListView.getRight()) / 2;
//        int cy = (resultsListView.getTop() + resultsListView.getBottom()) / 2;

        int location[] = {0, 0};
        launcherButton.getLocationInWindow(location);
        int cx = location[0];
        int cy = location[1];
        int duration = 300; //Normally 300. Can set to 1000 to show off animation more

        // get the final radius for the clipping circle
//        int finalRadius = Math.max(kissBar.getWidth(), kissBar.getHeight());

        // CH 3/7/16 - Multiplied this by 1.25 because it went a little away from the corner and then
        // just instantly filled it without doing that corner in the animation because it was only doing it with the radius
        // of the height of the screen. This was too short since it really needs to go from corner to corner.
        int finalRadius = (int) Math.round(1.25 * (Math.max(resultsListView.getWidth(), resultsListView.getHeight())));

        if (display) {
            // Display the app list
//            resultsListView.setAdapter(adapter);
            kissBar.setVisibility(View.GONE);

            if (searcher != null) {
                searcher.cancel(true);
            }
            searcher = new ApplicationsSearcher(MainActivity.this);
            searcher.execute();

            // Reveal the bar
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Animator anim = ViewAnimationUtils.createCircularReveal(resultsListView, cx, cy, 0, finalRadius);
                anim.setDuration(duration);
                resultsListView.setVisibility(View.VISIBLE);
                anim.start();
            } else {
                // No animation before Lollipop
                resultsListView.setVisibility(View.VISIBLE);
            }

            // Retrieve favorites. Try to retrieve more, since some favorites can't be displayed (e.g. search queries)
//            retrieveFavorites();

            hideKeyboard();
        } else {
            // Hide the bar
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

                Animator anim = ViewAnimationUtils.createCircularReveal(resultsListView, cx, cy, finalRadius, 0);
                anim.setDuration(duration);

                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        resultsListView.setVisibility(View.GONE);
                        searchEditText.setText("");
                    }
                });
                anim.start();

            } else {
                // No animation before Lollipop
                resultsListView.setVisibility(View.GONE);
                searchEditText.setText("");
            }
        }
    }

    private void displayFavoritesBar(Boolean display) {
        final ImageView launcherButton = (ImageView) findViewById(R.id.launcherButton2);
        ArrayList<Pojo> favoritesPojo = KissApplication.getDataHandler(MainActivity.this)
                .getFavorites(tryToRetrieve);

        int location[] = {0, 0};
        launcherButton.getLocationInWindow(location);
        int startX = location[0];
        int startY = location[1];
        int duration = 300; //Normally 300. Can set to 1000 to show off animation more

        if (display) {

            resultsListView.setVisibility(View.GONE);
            searchEditText.setText("");

            if (isEmptyMenuVisible) {
                main_empty_layout.setVisibility(View.VISIBLE);
            }

//            kissBar.setVisibility(View.INVISIBLE);

            if (favoritesPojo.size() > 0) {

                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

                    for (int i = 0; i < favoritesPojo.size(); i++) {
//                        if (i < 4) {
                        FrameLayout layout = (FrameLayout) findViewById(favsLayoutIds[i]);

                        int endLocation[] = {0, 0};
                        layout.getLocationInWindow(endLocation);
                        int endX = endLocation[0];
                        int endY = endLocation[1];
                        int deltaX = startX - endX;
                        int deltaY = startY - endY;

                        AnimationSet animation = new AnimationSet(true);
                        RotateAnimation rotateAnimation = new RotateAnimation(-180, 0, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                        rotateAnimation.setDuration(duration);
                        animation.addAnimation(rotateAnimation);

                        TranslateAnimation transAnimation = new TranslateAnimation(Animation.ABSOLUTE, deltaX, Animation.ABSOLUTE,
                                0, Animation.ABSOLUTE, deltaY, Animation.ABSOLUTE, 0);
                        transAnimation.setDuration(duration);
                        animation.addAnimation(transAnimation);

//                        transAnimation.setStartOffset(0);
                        kissBar.setVisibility(View.VISIBLE);
                        layout.startAnimation(animation);
                    }
                } else {
                    // No animation before Lollipop
                    kissBar.setVisibility(View.VISIBLE);
                }
            }

            // Retrieve favorites. Try to retrieve more, since some favorites can't be displayed (e.g. search queries)
            retrieveFavorites();

            hideKeyboard();
        } else {

            if (favoritesPojo.size() > 0) {
                // Hide the bar
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    for (int i = 0; i < favoritesPojo.size(); i++) {
//                        if (i < 4) {
                        FrameLayout layout = (FrameLayout) findViewById(favsLayoutIds[i]);

                        int endLocation[] = {0, 0};
                        layout.getLocationInWindow(endLocation);
                        int endX = endLocation[0];
                        int endY = endLocation[1];
                        int deltaX = startX - endX;
                        int deltaY = startY - endY;

                        AnimationSet animation = new AnimationSet(true);
                        RotateAnimation rotateAnimation = new RotateAnimation(0, -180, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                        rotateAnimation.setDuration(duration);
                        animation.addAnimation(rotateAnimation);

                        TranslateAnimation transAnimation = new TranslateAnimation(Animation.ABSOLUTE, 0, Animation.ABSOLUTE,
                                deltaX, Animation.ABSOLUTE, 0, Animation.ABSOLUTE, deltaY);
                        transAnimation.setDuration(duration);
                        animation.addAnimation(transAnimation);
//                        transAnimation.setStartOffset(0);
//                        kissBar.setVisibility(View.GONE);
                        layout.startAnimation(animation);
                        layout.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                kissBar.setVisibility(View.GONE);
                            }
                        }, duration);
                    }
                } else {
                    // No animation before Lollipop
                    kissBar.setVisibility(View.GONE);
                }
            }

            if (isEmptyMenuVisible) {
//                TranslateAnimation emptyMenuAnimation = new TranslateAnimation(0, 0, 0, 0);
//                emptyMenuAnimation.setDuration(duration);
//                main_empty_layout.startAnimation(emptyMenuAnimation);
//                main_empty_layout.postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        main_empty_layout.setVisibility(View.VISIBLE);
//                    }
//                }, duration);
                main_empty_layout.setVisibility(View.VISIBLE);
            }

            searchEditText.setText("");
        }
    }

    public void retrieveFavorites() {
        ArrayList<Pojo> favoritesPojo = KissApplication.getDataHandler(MainActivity.this)
                .getFavorites(tryToRetrieve);
        boolean isAtLeast1FavoriteApp = false;

        if (favoritesPojo.size() == 0) {
            Toast toast = Toast.makeText(MainActivity.this, getString(R.string.no_favorites), Toast.LENGTH_SHORT);
            toast.show();
            isAtLeast1FavoriteApp = false;
        } else if (favoritesPojo.size() > 0) {
            isAtLeast1FavoriteApp = true;
        }

        if (isAtLeast1FavoriteApp && isEmptyMenuVisible) {
            main_empty_layout.setVisibility(View.INVISIBLE);
        }

        // Don't look for items after favIds length, we won't be able to display them
        for (int i = 0; i < Math.min(favsIds.length, favoritesPojo.size()); i++) {
            Pojo pojo = favoritesPojo.get(i);
            ImageView image = (ImageView) findViewById(favsIds[i]);
            ImageView circle = (ImageView) findViewById(favsCircleIds[i]);

            Result result = Result.fromPojo(MainActivity.this, pojo);
            Drawable drawable = result.getDrawable(MainActivity.this);
            if (drawable != null)
                image.setImageDrawable(drawable);
            image.setVisibility(View.VISIBLE);
            circle.setVisibility(View.VISIBLE);
            image.setContentDescription(pojo.displayName);
        }

        // Hide empty favorites (not enough favorites yet)
        for (int i = favoritesPojo.size(); i < favsIds.length; i++) {
            findViewById(favsIds[i]).setVisibility(View.GONE);
            findViewById(favsCircleIds[i]).setVisibility(View.GONE);
        }
    }

    /**
     * This function gets called on changes. It will ask all the providers for
     * data
     *
     * @param query the query on which to search
     */

    private void updateRecords(String query) {
        if (searcher != null) {
            searcher.cancel(true);
        }

        if (query.length() == 0) {
            if (prefs.getBoolean("history-hide", false)) {
                searcher = new NullSearcher(this);
                //Hide default scrollview
                findViewById(R.id.main_empty).setVisibility(View.INVISIBLE);
                isEmptyMenuVisible = false;

            } else {
                searcher = new HistorySearcher(this);
                //Show default scrollview
                findViewById(R.id.main_empty).setVisibility(View.VISIBLE);
                isEmptyMenuVisible = true;
            }
        } else {
            searcher = new QuerySearcher(this, query);
        }
        searcher.execute();
    }

    public void resetTask() {
        searcher = null;
    }

    /**
     * Call this function when we're leaving the activity We can't use
     * onPause(), since it may be called for a configuration change
     */
    public void launchOccurred(int index, Result result) {
        // We selected an item on the list,
        // now we can cleanup the filter:
        if (!searchEditText.getText().toString().equals("")) {
            searchEditText.setText("");
            hideKeyboard();
        }
    }

    private void hideKeyboard() {
        // Check if no view has focus:
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager inputManager = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    private void showKeyboard() {
        searchEditText.requestFocus();
        InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        mgr.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
    }

    public int getFavIconsSize() {
        return favsIds.length;
    }
}