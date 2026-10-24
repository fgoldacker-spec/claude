package de.goldacker.notruf;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Edit emergency contacts (order = call order), own name and SMS text. */
public class SettingsActivity extends Activity {
    private static final int PICK_CONTACT = 1;

    private List<Prefs.Contact> contacts;
    private LinearLayout list;
    private EditText numberInput;
    private EditText nameInput;
    private EditText messageInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        contacts = Prefs.contacts(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        root.addView(heading("Notfallnummern"));
        root.addView(note("Bei Alarm bekommen alle gleichzeitig eine SMS mit deinem Standort. "
            + "Angerufen wird nacheinander in dieser Reihenfolge."));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        LinearLayout addRow = new LinearLayout(this);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        numberInput = new EditText(this);
        numberInput.setHint("Telefonnummer");
        numberInput.setInputType(InputType.TYPE_CLASS_PHONE);
        addRow.addView(numberInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button add = new Button(this);
        add.setText("Hinzufügen");
        add.setOnClickListener(v -> {
            addContact("", numberInput.getText().toString());
            numberInput.setText("");
        });
        addRow.addView(add);
        root.addView(addRow);

        Button pick = new Button(this);
        pick.setText("Aus Kontakten wählen");
        pick.setOnClickListener(v -> pickContact());
        root.addView(pick);

        root.addView(heading("Dein Name (steht in der SMS)"));
        nameInput = new EditText(this);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nameInput.setText(Prefs.name(this));
        root.addView(nameInput);

        root.addView(heading("SMS-Text"));
        messageInput = new EditText(this);
        messageInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        messageInput.setMinLines(3);
        messageInput.setGravity(Gravity.TOP | Gravity.START);
        messageInput.setText(Prefs.message(this));
        root.addView(messageInput);
        root.addView(note("Der Standort-Link wird automatisch angehängt."));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        renderList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Prefs.setNameAndMessage(this, nameInput.getText().toString(), messageInput.getText().toString());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void renderList() {
        list.removeAllViews();
        if (contacts.isEmpty()) {
            list.addView(note("Noch keine Nummern eingetragen."));
        }
        for (int i = 0; i < contacts.size(); i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(4));

            TextView label = new TextView(this);
            label.setText((i + 1) + ".  " + contacts.get(i).label());
            label.setTextColor(0xFFFFFFFF);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            if (i > 0) {
                Button up = smallButton("↑", "Nach oben");
                up.setOnClickListener(v -> {
                    contacts.add(index - 1, contacts.remove(index));
                    save();
                });
                row.addView(up);
            }
            Button del = smallButton("✕", "Löschen");
            del.setOnClickListener(v -> {
                contacts.remove(index);
                save();
            });
            row.addView(del);
            list.addView(row);
        }
    }

    private void addContact(String name, String rawNumber) {
        String number = Prefs.cleanNumber(rawNumber);
        if (number.replace("+", "").length() < 3) {
            Toast.makeText(this, "Bitte eine gültige Telefonnummer eingeben.", Toast.LENGTH_SHORT).show();
            return;
        }
        for (Prefs.Contact c : contacts) {
            if (c.number.equals(number)) {
                Toast.makeText(this, "Nummer ist schon eingetragen.", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        contacts.add(new Prefs.Contact(name, number));
        save();
    }

    private void save() {
        Prefs.setContacts(this, contacts);
        renderList();
    }

    private void pickContact() {
        // The picker grants read access to the chosen entry, so no contacts permission is needed.
        Intent i = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        try {
            startActivityForResult(i, PICK_CONTACT);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Keine Kontakte-App gefunden.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_CONTACT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        String[] projection = {
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        };
        try (Cursor c = getContentResolver().query(uri, projection, null, null, null)) {
            if (c != null && c.moveToFirst()) addContact(c.getString(0), c.getString(1));
        } catch (RuntimeException e) {
            Toast.makeText(this, "Kontakt konnte nicht gelesen werden.", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView heading(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFFF5252);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setPadding(0, dp(20), 0, dp(6));
        return t;
    }

    private TextView note(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFAAAAAA);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    private Button smallButton(String text, String description) {
        Button b = new Button(this);
        b.setText(text);
        b.setContentDescription(description);
        b.setMinWidth(dp(48));
        b.setMinimumWidth(dp(48));
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
