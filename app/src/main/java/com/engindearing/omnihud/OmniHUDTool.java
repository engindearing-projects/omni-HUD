package com.engindearing.omnihud;

import android.content.Context;

import com.atak.plugins.impl.AbstractPluginTool;
import com.atakmap.coremap.log.Log;
import gov.tak.api.util.Disposable;

public class OmniHUDTool extends AbstractPluginTool implements Disposable {

    private static final String TAG = OmniHUDTool.class.getSimpleName();

    public OmniHUDTool(final Context context) {
        super(context,
                context.getString(R.string.app_name),
                context.getString(R.string.app_name),
                context.getResources().getDrawable(R.drawable.ic_launcher),
                OmniHUDDropDownReceiver.SHOW_PLUGIN);

        Log.d(TAG, "OmniHUDTool initialized");
    }

    @Override
    public void dispose() {
        // Cleanup if needed
    }
}
