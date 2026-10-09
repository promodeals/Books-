package com.example.books;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;

public class PdfViewerActivity extends Activity {
    private ParcelFileDescriptor descriptor;
    private PdfRenderer renderer;
    private ImageView pageImage;
    private TextView pageLabel;
    private int pageIndex = 0;

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(35, 45, 38));
        getWindow().setNavigationBarColor(Color.rgb(35, 45, 38));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(242, 241, 236));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(4), dp(8), dp(4));
        bar.setBackgroundColor(Color.rgb(255, 255, 255));

        Button previous = new Button(this);
        previous.setText("Previous");
        previous.setAllCaps(false);
        previous.setOnClickListener(v -> showPage(pageIndex - 1));
        bar.addView(previous, new LinearLayout.LayoutParams(0, dp(48), 1));

        pageLabel = new TextView(this);
        pageLabel.setGravity(Gravity.CENTER);
        pageLabel.setTextColor(Color.rgb(32, 36, 43));
        bar.addView(pageLabel, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button next = new Button(this);
        next.setText("Next");
        next.setAllCaps(false);
        next.setOnClickListener(v -> showPage(pageIndex + 1));
        bar.addView(next, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        pageImage = new ImageView(this);
        pageImage.setAdjustViewBounds(true);
        pageImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pageImage.setBackgroundColor(Color.WHITE);
        scroll.addView(pageImage, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        String path = getIntent().getStringExtra("pdf_path");
        if (path == null || !new File(path).isFile()) {
            Toast.makeText(this, "This PDF file could not be found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try {
            descriptor = ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(descriptor);
            if (renderer.getPageCount() == 0) {
                Toast.makeText(this, "This PDF has no pages.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            showPage(0);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open this PDF. The file may be damaged or protected.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void showPage(int requested) {
        if (renderer == null) return;
        if (requested < 0 || requested >= renderer.getPageCount()) return;
        pageIndex = requested;
        PdfRenderer.Page page = null;
        Bitmap bitmap = null;
        try {
            page = renderer.openPage(pageIndex);
            int availableWidth = Math.max(dp(280), getResources().getDisplayMetrics().widthPixels - dp(24));
            float scale = Math.min(2.0f, availableWidth / (float) page.getWidth());
            int width = Math.max(1, (int) (page.getWidth() * scale));
            int height = Math.max(1, (int) (page.getHeight() * scale));
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            pageImage.setImageBitmap(bitmap);
            pageLabel.setText((pageIndex + 1) + " / " + renderer.getPageCount());
        } catch (Exception e) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            Toast.makeText(this, "Could not render this page.", Toast.LENGTH_SHORT).show();
        } finally {
            if (page != null) page.close();
        }
    }

    @Override
    protected void onDestroy() {
        if (renderer != null) renderer.close();
        if (descriptor != null) {
            try { descriptor.close(); } catch (IOException ignored) {}
        }
        super.onDestroy();
    }
}
