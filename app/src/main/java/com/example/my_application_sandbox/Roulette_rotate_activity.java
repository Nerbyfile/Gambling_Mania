package com.example.my_application_sandbox;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class Roulette_rotate_activity extends AppCompatActivity {



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_roulette_rotate);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        View wheel = findViewById(R.id.wheel);
        View ballOrbit = findViewById(R.id.ball_orbit);
        View ball = findViewById(R.id.ball);
        Button btnSpin = findViewById(R.id.btn_spin);
        MiniTableView miniTable = findViewById(R.id.mini_table);
        miniTable.setBets(Roulette_player.getInstance().getBets());

        btnSpin.setOnClickListener(v -> {
            Roulette_player player = Roulette_player.getInstance();
            if (player.getBets().isEmpty()) {
                Toast.makeText(v.getContext(), "Aucun pari en cours", Toast.LENGTH_SHORT).show();
                return;
            }
            btnSpin.setEnabled(false);

            miniTable.setWinning(-1);

            int winning = new java.util.Random().nextInt(37);     // ! Non sécurisé & anticipable random extern nécéssaire
            float a = RouletteWheelView.angleOfNumber(winning); // 0..360

            float dp = getResources().getDisplayMetrics().density;
            float R = wheel.getWidth() / 2f;
            float ballMarginTop = 10f * dp;
            float ballRadius = ball.getHeight() / 2f;
            // distance du haut de la roue jusqu'au bas des cases = 38 % du rayon
            float drop = R * 0.35f - (ballMarginTop + ballRadius);

            ball.setTranslationY(0f);

            // La roue fait 3 tours complets (1080° ≡ 0°), donc elle finit orientée comme au départ
            ObjectAnimator wheelSpin = ObjectAnimator.ofFloat(wheel, "rotation", 0f, 1080f);
            wheelSpin.setDuration(6000);
            wheelSpin.setInterpolator(new DecelerateInterpolator());

            // La balle tourne en sens inverse et finit à l'angle de la case gagnante
            ObjectAnimator ballSpin = ObjectAnimator.ofFloat(ballOrbit, "rotation", 0f, a - 2160f);
            ballSpin.setDuration(6000);
            ballSpin.setInterpolator(new DecelerateInterpolator());

            ObjectAnimator ballDrop = ObjectAnimator.ofFloat(ball, "translationY", 0f, drop);
            ballDrop.setDuration(2500);
            ballDrop.setStartDelay(3500);
            ballDrop.setInterpolator(new BounceInterpolator());

            AnimatorSet set = new AnimatorSet();
            set.playTogether(wheelSpin, ballSpin, ballDrop);
            set.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    btnSpin.setEnabled(true);
                    int payout = player.settle(winning);   // crédite les gains et vide les paris
                    miniTable.setWinning(winning);               // entoure le numéro gagnant
                    miniTable.setBets(player.getBets());         // les paris sont vidés par settle()
                    RouletteHistory.addSolo(winning);
                    PlayerStore.save(btnSpin.getContext(), player.getPlayer());   // paris vidés : sûr de sauvegarder
                    PlayerRepository.updateBank(player.getPlayer());
                    String msg = "Numéro : " + winning
                            + (payout > 0 ? " — tu récupères " + payout : " — perdu")
                            + "\nSolde : " + player.getBank();
                    Toast.makeText(btnSpin.getContext(), msg, Toast.LENGTH_LONG).show();
                }
            });
            set.start();
        });
    }






// Le pivot par défaut d'une View est son centre : rien à configurer.


}