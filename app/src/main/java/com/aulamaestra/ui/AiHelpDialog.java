package com.aulamaestra.ui;

import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.aulamaestra.R;
import com.aulamaestra.db.AulaRepository;
import com.aulamaestra.db.RepoCallback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public final class AiHelpDialog {
    private AiHelpDialog() {
    }

    public static void show(AppCompatActivity activity) {
        View form = activity.getLayoutInflater().inflate(R.layout.dialog_ai_help, null);
        TextInputEditText question = form.findViewById(R.id.inputAiQuestion);
        TextView answer = form.findViewById(R.id.textAiAnswer);
        ProgressBar progress = form.findViewById(R.id.progressAiHelp);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ai_help_title)
                .setView(form)
                .setPositiveButton(R.string.ask_ai, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = question.getText() == null ? "" : question.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(activity, R.string.ai_help_write_question, Toast.LENGTH_SHORT).show();
                return;
            }
            v.setEnabled(false);
            progress.setVisibility(View.VISIBLE);
            answer.setVisibility(View.GONE);
            AulaRepository.get().askAiHelp(text, new RepoCallback<String>() {
                @Override
                public void onSuccess(String response) {
                    v.setEnabled(true);
                    progress.setVisibility(View.GONE);
                    answer.setText(response);
                    answer.setVisibility(View.VISIBLE);
                }

                @Override
                public void onError(String message) {
                    v.setEnabled(true);
                    progress.setVisibility(View.GONE);
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
