package com.example.my_application_sandbox;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.RelativeCornerSize;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.function.IntConsumer;

/** Popup « Choisis ton avatar » : grille 4x2, renvoie le numéro choisi. */
public final class AvatarPicker {

    private AvatarPicker() { }

    public static void show(Context ctx, int currentId, IntConsumer onPick) {
        float d = ctx.getResources().getDisplayMetrics().density;
        int size = (int) (56 * d), margin = (int) (6 * d), pad = (int) (24 * d);
        AlertDialog[] holder = new AlertDialog[1];

        GridLayout grid = new GridLayout(ctx);
        grid.setColumnCount(4);

        for (int i = 0; i < Avatar.COUNT; i++) {
            final int id = i;
            ShapeableImageView iv = new ShapeableImageView(ctx);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = size;
            lp.height = size;
            lp.setMargins(margin, margin, margin, margin);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageResource(Avatar.res(id));
            iv.setShapeAppearanceModel(ShapeAppearanceModel.builder()
                    .setAllCornerSizes(new RelativeCornerSize(0.5f)).build());   // rond
            boolean selected = (id == currentId);
            iv.setStrokeColor(ColorStateList.valueOf(selected ? 0xFFF5D78A : 0x55C9A227));
            iv.setStrokeWidth((selected ? 4 : 2) * d);
            iv.setOnClickListener(v -> {
                onPick.accept(id);
                holder[0].dismiss();
            });
            grid.addView(iv);
        }

        FrameLayout box = new FrameLayout(ctx);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(grid, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL));

        holder[0] = new MaterialAlertDialogBuilder(ctx)
                .setTitle("Choisis ton avatar")
                .setView(box)
                .setNegativeButton("Annuler", null)
                .create();
        holder[0].show();
    }
}