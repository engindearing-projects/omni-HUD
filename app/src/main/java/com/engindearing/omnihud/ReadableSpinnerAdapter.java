package com.engindearing.omnihud;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

/**
 * Custom ArrayAdapter that ensures readable text colors for Spinner
 * Selected item: white text
 * Dropdown items: black text on white background
 */
public class ReadableSpinnerAdapter extends ArrayAdapter<String> {

    public ReadableSpinnerAdapter(Context context, List<String> items) {
        super(context, android.R.layout.simple_spinner_item, items);
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        TextView textView = (TextView) view;
        // Selected item - white text for dark ATAK theme
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(16);
        return view;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        View view = super.getDropDownView(position, convertView, parent);
        TextView textView = (TextView) view;
        // Dropdown items - black text on white background for maximum readability
        textView.setTextColor(Color.BLACK);
        textView.setBackgroundColor(Color.WHITE);
        textView.setTextSize(16);
        textView.setPadding(20, 20, 20, 20);
        return view;
    }
}
