package com.google.collinsmith70.diablo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Timer;
import com.google.collinsmith70.diablo.cvar.Cvar;
import com.google.collinsmith70.diablo.cvar.CvarChangeListener;
import com.google.collinsmith70.diablo.cvar.Cvars;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class Console implements Disposable {
private static final String TAG = Console.class.getSimpleName();
private static final int CONSOLE_BUFFER_SIZE = 128;
private static final float CARET_HOLD_DELAY = 1.0f;
private static final float CARET_BLINK_DELAY = 0.5f;

private final Client CLIENT;
private final Set<CommandProcessor> commandProcessors;
private BitmapFont font;

private final Timer CARET_TIMER;
private final Timer.Task CARET_BLINK_TASK;

private StringBuffer consoleBuffer;
private boolean isVisible;
private boolean showCaret;
private int caretPosition;

private SortedMap<String, Cvar<?>> prefixedCvars;
private Iterator<Map.Entry<String, Cvar<?>>> prefixedCvarsIterator;
private Map.Entry<String, Cvar<?>> currentlyReadCvar;

public Console(Client client) {
    this.CLIENT = client;
    this.commandProcessors = new CopyOnWriteArraySet<CommandProcessor>();
    this.isVisible = false;

    final CvarChangeListener<Float> consoleFontColorCvarListener = new CvarChangeListener<Float>() {
        @Override
        public void onCvarChanged(Cvar<Float> cvar, Float fromValue, Float toValue) {
            if (cvar.equals(Cvars.Client.Overlay.ConsoleFontColor.a)) {
                font.getColor().a = toValue;
            } else if (cvar.equals(Cvars.Client.Overlay.ConsoleFontColor.r)) {
                font.getColor().r = toValue;
            } else if (cvar.equals(Cvars.Client.Overlay.ConsoleFontColor.g)) {
                font.getColor().g = toValue;
            } else if (cvar.equals(Cvars.Client.Overlay.ConsoleFontColor.b)) {
                font.getColor().b = toValue;
            }
        }
    };

    Cvars.Client.Overlay.ConsoleFont.addCvarChangeListener(new CvarChangeListener<AssetDescriptor<BitmapFont>>() {
        @Override
        public void onCvarChanged(Cvar<AssetDescriptor<BitmapFont>> cvar, AssetDescriptor<BitmapFont> fromValue, AssetDescriptor<BitmapFont> toValue) {
            Console.this.getClient().getAssetManager().load(toValue);
            Console.this.getClient().getAssetManager().finishLoading();

            Console.this.font = CLIENT.getAssetManager().get(Cvars.Client.Overlay.ConsoleFont.getValue());
            Cvars.Client.Overlay.ConsoleFontColor.r.addCvarChangeListener(consoleFontColorCvarListener);
            Cvars.Client.Overlay.ConsoleFontColor.g.addCvarChangeListener(consoleFontColorCvarListener);
            Cvars.Client.Overlay.ConsoleFontColor.b.addCvarChangeListener(consoleFontColorCvarListener);
            Cvars.Client.Overlay.ConsoleFontColor.a.addCvarChangeListener(consoleFontColorCvarListener);
        }
    });

    CARET_TIMER = new Timer();
    CARET_BLINK_TASK = new Timer.Task() {
        @Override
        public void run() {
            Console.this.showCaret = !Console.this.showCaret;
        }
    };

    clearBuffer();
    updateCaret();
    CARET_TIMER.start();
}

public Client getClient() {
    return CLIENT;
}

public BitmapFont getFont() {
    return font;
}

public boolean isVisible() {
    return isVisible;
}

public void setVisible(boolean b) {
    this.isVisible = b;
    if (isVisible()) {
        updateCaret();
    }
}

public void addCommandProcessor(CommandProcessor p) {
    commandProcessors.add(p);
}

public boolean containsCommandProcessor(CommandProcessor p) {
    return commandProcessors.contains(p);
}

public boolean removeCommandProcessor(CommandProcessor p) {
    return commandProcessors.remove(p);
}

public void clearBuffer() {
    consoleBuffer = new StringBuffer(CONSOLE_BUFFER_SIZE);
    caretPosition = 0;
}

public String getBuffer() {
    return consoleBuffer.toString();
}

public boolean keyDown(int keycode) {
    switch (keycode) {
        case Input.Keys.LEFT:
            caretPosition = Math.max(caretPosition - 1, 0);
            updateCaret();
            return true;
        case Input.Keys.RIGHT:
            caretPosition = Math.min(caretPosition + 1, consoleBuffer.length());
            updateCaret();
            return true;
        case Input.Keys.UP:
            updateCaret();
            return true;
        case Input.Keys.DOWN:
            updateCaret();
            return true;
        case Input.Keys.TAB:
            if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
                if (consoleBuffer.length() != 0) {
                    do {
                        keyTyped('\b');
                    } while (consoleBuffer.length() != 0
                          && consoleBuffer.charAt(caretPosition-1) != '.');
                }
            } else if (prefixedCvarsIterator.hasNext() || currentlyReadCvar != null) {
                if (currentlyReadCvar == null) {
                    currentlyReadCvar = prefixedCvarsIterator.next();
                    Gdx.app.log(TAG, "CURRENT = " + currentlyReadCvar.getKey());
                }

                clearBuffer();
                for (char ch : currentlyReadCvar.getKey().toCharArray()) {
                    keyTyped(ch, false);
                    /*if (ch == '.') {
                        break;
                    }*/
                }

                if (caretPosition == currentlyReadCvar.getKey().length()) {
                    Gdx.app.log(TAG, "SETTING TO NULL");
                    currentlyReadCvar = null;
                }
            } else {
                prefixedCvarsIterator = prefixedCvars.entrySet().iterator();
                keyDown(Input.Keys.TAB);
                Gdx.app.log(TAG, "RESETTING");
                //Gdx.app.log(TAG, "INVALID " + prefixedCvarsIterator.hasNext() + " " + currentlyReadCvar);
            }

            return true;
        default:
            return false;
    }
}

private void updateCaret() {
    updateCaret(true);
}

private void updateCaret(boolean updateLookup) {
    CARET_BLINK_TASK.cancel();
    CARET_TIMER.schedule(CARET_BLINK_TASK, CARET_HOLD_DELAY, CARET_BLINK_DELAY);
    this.showCaret = true;

    if (prefixedCvars == null || (updateLookup && caretPosition == consoleBuffer.length())) {
        prefixedCvars = Cvar.search(getBuffer());
        prefixedCvarsIterator = prefixedCvars.entrySet().iterator();
        currentlyReadCvar = prefixedCvarsIterator.hasNext() ? prefixedCvarsIterator.next() : null;
        /*Gdx.app.log(TAG, "Ouputting keys:");
        int i = 0;
        for (String key : Cvar.lookup(getBuffer())) {
            Gdx.app.log(TAG, key);
            i++;
        }

        Gdx.app.log(TAG, i + " keys found");*/
    }
}

public boolean keyTyped(char ch) {
    return keyTyped(ch, true);
}

public boolean keyTyped(char ch, boolean updateLookup) {
    switch (ch) {
        case '\b':
            if (caretPosition > 0) {
                consoleBuffer.deleteCharAt(--caretPosition);
            }

            updateCaret(updateLookup);
            return true;
        case '\r':
        case '\n':
            boolean commandHandled = false;
            String command = consoleBuffer.toString();
            for (CommandProcessor p : commandProcessors) {
                if (p.process(command)) {
                    commandHandled = true;
                    break;
                }
            }

            if (!commandHandled) {
                Gdx.app.log(TAG, String.format("Unrecognized command: %s", command));
            }

            clearBuffer();
            updateCaret(updateLookup);
            return true;
        case 127:
            if (caretPosition < consoleBuffer.length()) {
                consoleBuffer.deleteCharAt(caretPosition);
            }

            updateCaret(updateLookup);
            return true;
        case 'a':case 'b':case 'c':case 'd':case 'e':case 'f':case 'g':case 'h':case 'i':case 'j':
        case 'k':case 'l':case 'm':case 'n':case 'o':case 'p':case 'q':case 'r':case 's':case 't':
        case 'u':case 'v':case 'w':case 'x':case 'y':case 'z':
        case 'A':case 'B':case 'C':case 'D':case 'E':case 'F':case 'G':case 'H':case 'I':case 'J':
        case 'K':case 'L':case 'M':case 'N':case 'O':case 'P':case 'Q':case 'R':case 'S':case 'T':
        case 'U':case 'V':case 'W':case 'X':case 'Y':case 'Z':
        case '0':case '1':case '2':case '3':case '4':case '5':case '6':case '7':case '8':case '9':
        case '-':case '_':case '.':case '\"':case ' ':
            consoleBuffer.insert(caretPosition++, ch);
            updateCaret(updateLookup);
            return true;
        default:
            return false;
    }
}

public void render(Batch b) {
    if (!isVisible()) {
        return;
    }

    GlyphLayout glyphs = font.draw(b, getBuffer(), 0, getClient().getVirtualHeight());
    if (showCaret) {
        glyphs.setText(font, getBuffer().substring(0, caretPosition));
        font.draw(b, "_", glyphs.width - 4, getClient().getVirtualHeight() - 1);
    }
}

@Override
public void dispose() {
    font.dispose();
}
}
