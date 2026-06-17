package com.aulamaestra.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.core.content.ContextCompat;

import com.aulamaestra.R;
import com.aulamaestra.db.AulaRepository;
import com.aulamaestra.db.RepoCallback;
import com.aulamaestra.model.ChatMessage;
import com.aulamaestra.model.Student;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageThreadActivity extends AppCompatActivity {
    public static final String EXTRA_SALON_ID = "salon_id";
    public static final String EXTRA_STUDENT_ID = "student_id";
    public static final String EXTRA_AS_TEACHER = "as_teacher";

    private final AulaRepository repo = AulaRepository.get();
    private long salonId;
    private long studentId;
    private boolean asTeacher;
    private MsgAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_thread);
        salonId = getIntent().getLongExtra(EXTRA_SALON_ID, -1L);
        studentId = getIntent().getLongExtra(EXTRA_STUDENT_ID, -1L);
        asTeacher = getIntent().getBooleanExtra(EXTRA_AS_TEACHER, false);
        if (salonId < 0 || studentId < 0) {
            finish();
            return;
        }
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        if (asTeacher) {
            repo.getStudent(studentId, new RepoCallback<Student>() {
                @Override
                public void onSuccess(Student st) {
                    toolbar.setTitle(getString(R.string.messages_with, st.displayName));
                }

                @Override
                public void onError(String message) {
                    toolbar.setTitle("Mensajes");
                }
            });
        } else {
            toolbar.setTitle(R.string.chat_teacher);
        }

        RecyclerView rv = findViewById(R.id.recyclerMessages);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MsgAdapter(new ArrayList<>(), asTeacher);
        rv.setAdapter(adapter);

        TextInputEditText input = findViewById(R.id.inputMessage);
        MaterialButton send = findViewById(R.id.btnSend);
        send.setOnClickListener(v -> {
            String body = input.getText() == null ? "" : input.getText().toString().trim();
            if (TextUtils.isEmpty(body)) {
                Toast.makeText(this, "Escribe un mensaje", Toast.LENGTH_SHORT).show();
                return;
            }
            repo.insertMessage(salonId, studentId, asTeacher, body, new RepoCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    input.setText("");
                    reload();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MessageThreadActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        repo.listMessages(salonId, studentId, new RepoCallback<List<ChatMessage>>() {
            @Override
            public void onSuccess(List<ChatMessage> list) {
                adapter.replace(list);
                RecyclerView rv = findViewById(R.id.recyclerMessages);
                if (adapter.getItemCount() > 0) {
                    rv.scrollToPosition(adapter.getItemCount() - 1);
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MessageThreadActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static class MsgAdapter extends RecyclerView.Adapter<MsgAdapter.VH> {
        private final List<ChatMessage> data;
        private final boolean viewerIsTeacher;
        private final DateFormat fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault());

        MsgAdapter(List<ChatMessage> data, boolean viewerIsTeacher) {
            this.data = data;
            this.viewerIsTeacher = viewerIsTeacher;
        }

        void replace(List<ChatMessage> list) {
            data.clear();
            data.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_line, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ChatMessage m = data.get(position);
            boolean mine = viewerIsTeacher ? m.fromTeacher : !m.fromTeacher;
            h.bubble.setText(m.body);
            if (mine) {
                h.bubble.setBackgroundResource(R.drawable.bg_chat_bubble_sent);
                h.bubble.setTextColor(ContextCompat.getColor(h.bubble.getContext(), R.color.chat_sent_text));
            } else {
                h.bubble.setBackgroundResource(R.drawable.bg_chat_bubble_received);
                h.bubble.setTextColor(ContextCompat.getColor(h.bubble.getContext(), R.color.chat_received_text));
            }
            String who = m.fromTeacher ? "Maestra" : "Alumno";
            h.meta.setText(who + " · " + fmt.format(new Date(m.createdAt)));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) h.row.getLayoutParams();
            lp.gravity = mine ? Gravity.END : Gravity.START;
            h.row.setLayoutParams(lp);
            h.meta.setGravity(mine ? Gravity.END : Gravity.START);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final LinearLayout row;
            final TextView bubble;
            final TextView meta;

            VH(@NonNull View itemView) {
                super(itemView);
                row = itemView.findViewById(R.id.messageRow);
                bubble = itemView.findViewById(R.id.textBubble);
                meta = itemView.findViewById(R.id.textMeta);
            }
        }
    }
}
