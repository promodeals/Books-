package com.example.books;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfViewerActivity extends Activity {
    private ParcelFileDescriptor descriptor;
    private PdfRenderer renderer;
    private ImageView pageImage;
    private TextView pageLabel;
    private int pageIndex = 0;
    private int requestId = 0;
    private Bitmap displayedBitmap;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService pdfExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed = false;

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
        bar.setBackgroundColor(Color.WHITE);

        Button previous = new Button(this);
        previous.setText("Previous");
        previous.setAllCaps(false);
        previous.setOnClickListener(v -> showPage(pageIndex - 1));
        bar.addView(previous, new LinearLayout.LayoutParams(0, dp(48), 1));

        pageLabel = new TextView(this);
        pageLabel.setGravity(Gravity.CENTER);
        pageLabel.setTextColor(Color.rgb(32, 36, 43));
        pageLabel.setText("Opening PDF…");
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

        // File opening and renderer setup can also take time, so do them off the UI thread.
        pdfExecutor.execute(() -> {
            try {
                ParcelFileDescriptor opened = ParcelFileDescriptor.open(
                    new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer openedRenderer = new PdfRenderer(opened);
                if (destroyed) {
                    openedRenderer.close();
                    opened.close();
                    return;
                }
                descriptor = opened;
                renderer = openedRenderer;
                if (renderer.getPageCount() == 0) {
                    mainHandler.post(() -> {
                        if (!destroyed) {
                            Toast.makeText(this, "This PDF has no pages.", Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });
                    return;
                }
                renderPage(0, ++requestId);
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!destroyed) {
                        Toast.makeText(this, "Could not open this PDF. It may be damaged or protected.", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        });
    }

    private void showPage(int requested) {
        PdfRenderer current = renderer;
        if (current == null || destroyed) return;
        if (requested < 0 || requested >= current.getPageCount()) return;
        pageIndex = requested;
        int id = ++requestId;
        pageLabel.setText("Loading " + (requested + 1) + "…");
        pdfExecutor.execute(() -> renderPage(requested, id));
    }

    // Runs only on the single PDF worker thread. Only one PdfRenderer.Page is open at a time.
    private void renderPage(int requested, int id) {
        Bitmap bitmap = null;
        PdfRenderer.Page page = null;
        try {
            if (destroyed || renderer == null) return;
            page = renderer.openPage(requested);
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int availableWidth = Math.max(1, screenWidth - dp(24));
            int sourceWidth = page.getWidth();
            int sourceHeight = page.getHeight();

            // Keep rasterized pages modest in size; huge scanned PDFs can otherwise exhaust RAM.
            // Conservative raster budget for low-memory Android phones.
            // Never force a minimum scale that could exceed the pixel budget.
            double scale = Math.min(1.0, availableWidth / (double) sourceWidth);
            scale = Math.min(scale, 1600.0 / sourceWidth);
            scale = Math.min(scale, 1200.0 / sourceHeight);
            scale = Math.min(scale, Math.sqrt(1200000.0 / ((double) sourceWidth * sourceHeight)));
            int width = Math.max(1, (int) Math.floor(sourceWidth * scale));
            int height = Math.max(1, (int) Math.floor(sourceHeight * scale));

            // RGB_565 uses about 2 bytes/pixel, keeping the working bitmap near 2.4 MB max.
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            Bitmap completed = bitmap;
            bitmap = null;
            mainHandler.post(() -> {
                if (destroyed || id != requestId) {
                    completed.recycle();
                    return;
                }
                Bitmap old = displayedBitmap;
                displayedBitmap = completed;
                pageImage.setImageBitmap(completed);
                pageLabel.setText((requested + 1) + " / " + renderer.getPageCount());
                if (old != null && old != completed && !old.isRecycled()) old.recycle();
            });
        } catch (Exception e) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            mainHandler.post(() -> {
                if (!destroyed && id == requestId) {
                    pageLabel.setText((requested + 1) + " / " + (renderer == null ? "?" : renderer.getPageCount()));
                    Toast.makeText(this, "Could not render this page. Try another page or close other apps.", Toast.LENGTH_SHORT).show();
                }
            });
        } finally {
            if (page != null) page.close();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        requestId++;
        pdfExecutor.execute(() -> {
            if (renderer != null) {
                try { renderer.close(); } catch (Exception ignored) {}
                renderer = null;
            }
            if (descriptor != null) {
                try { descriptor.close(); } catch (IOException ignored) {}
                descriptor = null;
            }
        });
        pdfExecutor.shutdown();
        if (displayedBitmap != null && !displayedBitmap.isRecycled()) {
            displayedBitmap.recycle();
            displayedBitmap = null;
        }
        super.onDestroy();
    }
}
