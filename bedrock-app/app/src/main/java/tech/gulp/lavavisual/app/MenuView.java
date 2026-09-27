package tech.gulp.lavavisual.app;

import android.content.Context;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** The menu behind the LV badge: every setting of the client, in sections. */
class MenuView extends FrameLayout {
    private static final String[] TABS = {"Стиль", "HUD", "Значок", "Помощь"};

    private final Cfg cfg;
    private final OverlayService overlay;
    private final LinearLayout content;
    private final LinearLayout tabs;
    private int tab;

    MenuView(Context context, Cfg cfg, OverlayService overlay) {
        super(context);
        this.cfg = cfg;
        this.overlay = overlay;
        setFocusableInTouchMode(true);
        setOnClickListener(view -> overlay.closeMenu());

        LinearLayout panel = Ui.column(context);
        panel.setBackground(Ui.card(Ui.BG, Ui.dp(context, 22), Ui.LINE));
        panel.setClickable(true);
        int pad = Ui.dp(context, 16);
        panel.setPadding(pad, pad, pad, pad);

        LinearLayout head = new LinearLayout(context);
        head.setGravity(Gravity.CENTER_VERTICAL);
        View mark = new View(context);
        mark.setBackground(Badge.drawable(context, cfg.accent(), 100));
        head.addView(mark, new LinearLayout.LayoutParams(Ui.dp(context, 34), Ui.dp(context, 34)));
        TextView title = Ui.text(context, "  LavaVisual", 19, Ui.TEXT, true);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView close = Ui.text(context, "✕", 20, Ui.DIM, true);
        close.setPadding(pad, Ui.dp(context, 4), Ui.dp(context, 4), Ui.dp(context, 4));
        close.setOnClickListener(view -> overlay.closeMenu());
        head.addView(close);
        panel.addView(head);

        tabs = new LinearLayout(context);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(tabs, Ui.wide(context, 12));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(true);
        content = Ui.column(context);
        scroll.addView(content);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        scrollParams.topMargin = Ui.dp(context, 6);
        panel.addView(scroll, scrollParams);

        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(
                Math.min(Ui.dp(context, 380), Math.round(getResources().getDisplayMetrics().widthPixels * 0.92f)),
                Math.round(getResources().getDisplayMetrics().heightPixels * 0.82f));
        place.gravity = Gravity.CENTER;
        addView(panel, place);

        drawTabs();
        drawContent();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            overlay.closeMenu();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void drawTabs() {
        tabs.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            TextView item = Ui.text(getContext(), TABS[i], 13, i == tab ? 0xFF0E0F13 : Ui.DIM, i == tab);
            item.setGravity(Gravity.CENTER);
            int padY = Ui.dp(getContext(), 8);
            item.setPadding(0, padY, 0, padY);
            item.setBackground(Ui.card(i == tab ? cfg.accent() : Ui.CARD, Ui.dp(getContext(), 11), i == tab ? 0 : Ui.LINE));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            params.rightMargin = i == TABS.length - 1 ? 0 : Ui.dp(getContext(), 6);
            item.setOnClickListener(view -> { tab = index; drawTabs(); drawContent(); });
            tabs.addView(item, params);
        }
    }

    private void add(View view) { content.addView(view, Ui.wide(getContext(), 0)); }

    private void section(String title, String hint) {
        content.addView(Ui.text(getContext(), title, 16, Ui.TEXT, true), Ui.wide(getContext(), 14));
        if (hint != null) content.addView(Ui.text(getContext(), hint, 12, Ui.DIM, false), Ui.wide(getContext(), 2));
    }

    private void drawContent() {
        android.util.Log.i("LavaVisual", "menu tab " + tab);
        content.removeAllViews();
        int accent = cfg.accent();
        switch (tab) {
            case 0: {
                section("Аксессуары", "Собираются в пак скина для твоего персонажа. Их видят и Bedrock, и Java-игроки.");
                add(Ui.toggle(getContext(), "Очки", null, cfg.glasses(), accent, on -> cfg.put("glasses", on)));
                add(Ui.toggle(getContext(), "Наушники", null, cfg.headphones(), accent, on -> cfg.put("headphones", on)));
                add(Ui.toggle(getContext(), "Шарф", null, cfg.scarf(), accent, on -> cfg.put("scarf", on)));
                add(Ui.toggle(getContext(), "Радужный шарф", "Полосы всех цветов вместо одного", cfg.rainbow(), accent, on -> cfg.put("rainbow", on)));
                add(Ui.toggle(getContext(), "Тонкие руки", "Как у скина Alex", cfg.slim(), accent, on -> cfg.put("slim", on)));
                section("Цвет", "Тот же набор цветов, что и в моде на Java.");
                add(Ui.palette(getContext(), cfg.colorCode(), accent, code -> cfg.put("colorCode", code)));
                String skin = cfg.skinUri().isEmpty() ? "Скин не выбран" : "Скин выбран";
                content.addView(Ui.text(getContext(), skin + " · нужен файл .png 64×64", 12, Ui.DIM, false), Ui.wide(getContext(), 10));
                add(Ui.button(getContext(), "Выбрать файл скина", accent, false, view -> skinAction("pick")));
                content.addView(Ui.button(getContext(), "Собрать и открыть пак", accent, true, view -> skinAction("build")), Ui.wide(getContext(), 10));
                break;
            }
            case 1: {
                section("HUD", "Цифры поверх игры.");
                add(Ui.toggle(getContext(), "Показывать HUD", null, cfg.hud(), accent, on -> { cfg.put("hud", on); overlay.refreshHud(); }));
                add(Ui.toggle(getContext(), "Часы", null, cfg.hudClock(), accent, on -> { cfg.put("hudClock", on); overlay.refreshHud(); }));
                add(Ui.toggle(getContext(), "FPS экрана", null, cfg.hudFps(), accent, on -> { cfg.put("hudFps", on); overlay.refreshHud(); }));
                add(Ui.toggle(getContext(), "Заряд батареи", null, cfg.hudBattery(), accent, on -> { cfg.put("hudBattery", on); overlay.refreshHud(); }));
                add(Ui.toggle(getContext(), "Пинг сервера", "Проверяется раз в 5 секунд", cfg.hudPing(), accent, on -> { cfg.put("hudPing", on); overlay.refreshHud(); }));
                EditText host = new EditText(getContext());
                host.setHint("адрес сервера, например play.example.com:19132");
                host.setText(cfg.pingHost());
                host.setTextColor(Ui.TEXT);
                host.setHintTextColor(Ui.DIM);
                host.setTextSize(14);
                host.setSingleLine(true);
                host.setBackground(Ui.card(Ui.CARD, Ui.dp(getContext(), 12), Ui.LINE));
                int pad = Ui.dp(getContext(), 12);
                host.setPadding(pad, pad, pad, pad);
                host.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                    @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                    @Override public void afterTextChanged(Editable s) { cfg.put("pingHost", s.toString()); }
                });
                content.addView(host, Ui.wide(getContext(), 8));
                add(Ui.slider(getContext(), "Размер", 9, 26, cfg.hudSize(), accent, value -> { cfg.put("hudSize", value); overlay.refreshHud(); }));
                content.addView(Ui.button(getContext(), overlay.movingHud() ? "Сохранить место" : "Переместить HUD", accent, false, view -> {
                    overlay.moveHud(!overlay.movingHud());
                    drawContent();
                }), Ui.wide(getContext(), 8));
                break;
            }
            case 2: {
                section("Значок LV", "Его можно таскать пальцем по экрану.");
                add(Ui.slider(getContext(), "Размер", 28, 72, cfg.badgeSize(), accent, value -> { cfg.put("badgeSize", value); overlay.refreshBadge(); }));
                add(Ui.slider(getContext(), "Непрозрачность", 20, 100, cfg.badgeAlpha(), accent, value -> { cfg.put("badgeAlpha", value); overlay.refreshBadge(); }));
                section("Цвет клиента", null);
                add(Ui.palette(getContext(), nearestCode(cfg.accent()), accent, code -> {
                    cfg.put("accent", Colors.opaque(code == 0 ? 0xFF7A2F : Colors.code(code)));
                    overlay.refreshBadge();
                    drawTabs();
                    drawContent();
                }));
                content.addView(Ui.button(getContext(), "Вернуть значок в угол", accent, false, view -> {
                    cfg.badgePos(Ui.dp(getContext(), 8), Ui.dp(getContext(), 90));
                    overlay.refreshBadge();
                }), Ui.wide(getContext(), 10));
                content.addView(Ui.button(getContext(), "Выключить оверлей", accent, false, view -> {
                    overlay.closeMenu();
                    getContext().stopService(new Intent(getContext(), OverlayService.class));
                }), Ui.wide(getContext(), 10));
                break;
            }
            default: {
                section("Что умеет клиент", null);
                content.addView(Ui.text(getContext(),
                        "1. Значок LV висит поверх игры и открывает это меню.\n"
                        + "2. HUD показывает часы, FPS экрана, батарею и пинг сервера.\n"
                        + "3. Аксессуары собираются в пак скина .mcpack: выбери свой скин 64×64, нажми «Собрать и открыть»,"
                        + " Minecraft импортирует пак, затем Профиль → Внешний вид → LavaVisual.\n\n"
                        + "Аксессуары видят все игроки Bedrock. Игроки Java увидят их, если на сервере стоит Geyser"
                        + " с расширением LavaVisual Bedrock.\n\n"
                        + "Приложение ничего не меняет внутри Minecraft и не трогает его файлы.",
                        13, Ui.DIM, false), Ui.wide(getContext(), 6));
                content.addView(Ui.text(getContext(), "Версия 1.0.0", 12, Ui.DIM, false), Ui.wide(getContext(), 12));
                break;
            }
        }
    }

    private int nearestCode(int accent) {
        int best = 1;
        double bestDistance = Double.MAX_VALUE;
        for (int code = 1; code < 32; code++) {
            int rgb = Colors.code(code);
            double d = Math.pow((rgb >> 16 & 255) - (accent >> 16 & 255), 2)
                    + Math.pow((rgb >> 8 & 255) - (accent >> 8 & 255), 2)
                    + Math.pow((rgb & 255) - (accent & 255), 2);
            if (d < bestDistance) { bestDistance = d; best = code; }
        }
        return best;
    }

    private void skinAction(String action) {
        overlay.closeMenu();
        Intent intent = new Intent(getContext(), SkinActivity.class)
                .putExtra("action", action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        getContext().startActivity(intent);
    }
}
