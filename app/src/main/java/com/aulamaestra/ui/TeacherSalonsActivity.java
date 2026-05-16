package com.aulamaestra.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aulamaestra.R;
import com.aulamaestra.db.AulaRepository;
import com.aulamaestra.db.RepoCallback;
import com.aulamaestra.db.SessionManager;
import com.aulamaestra.model.Salon;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

public class TeacherSalonsActivity extends AppCompatActivity {
    private final AulaRepository repo = AulaRepository.get();
    private SessionManager session;
    private SalonAdapter adapter;
    private long teacherId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_salons);
        session = new SessionManager(this);
        teacherId = session.getTeacherId();
        if (teacherId < 0) {
            finish();
            return;
        }
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> {
            session.clearTeacher();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        RecyclerView rv = findViewById(R.id.recyclerSalons);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SalonAdapter(new ArrayList<>(), salon -> {
            Intent i = new Intent(this, TeacherSalonActivity.class);
            i.putExtra(TeacherSalonActivity.EXTRA_SALON_ID, salon.id);
            startActivity(i);
        });
        rv.setAdapter(adapter);
        loadSalons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (teacherId >= 0) {
            loadSalons();
        }
    }

    private void loadSalons() {
        repo.listSalonsForTeacher(teacherId, new RepoCallback<List<Salon>>() {
            @Override
            public void onSuccess(List<Salon> salons) {
                adapter.update(salons);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(TeacherSalonsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static class SalonAdapter extends RecyclerView.Adapter<SalonAdapter.VH> {
        interface Listener {
            void onOpen(Salon salon);
        }

        private List<Salon> data;
        private final Listener listener;

        SalonAdapter(List<Salon> data, Listener listener) {
            this.data = data;
            this.listener = listener;
        }

        void update(List<Salon> salons) {
            this.data = salons;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_salon, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Salon s = data.get(position);
            h.title.setText("Salón " + s.number);
            h.code.setText(h.itemView.getContext().getString(R.string.invite_code_label, s.inviteCode));
            h.itemView.setOnClickListener(v -> listener.onOpen(s));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView code;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.textSalonTitle);
                code = itemView.findViewById(R.id.textSalonCode);
            }
        }
    }
}
