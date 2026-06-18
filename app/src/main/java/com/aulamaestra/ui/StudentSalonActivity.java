package com.aulamaestra.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.aulamaestra.R;
import com.aulamaestra.db.AulaRepository;
import com.aulamaestra.db.RepoCallback;
import com.aulamaestra.model.Salon;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class StudentSalonActivity extends AppCompatActivity {
    public static final String EXTRA_SALON_ID = "salon_id";
    public static final String EXTRA_STUDENT_ID = "student_id";

    private final AulaRepository repo = AulaRepository.get();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_salon);
        long salonId = getIntent().getLongExtra(EXTRA_SALON_ID, -1L);
        long studentId = getIntent().getLongExtra(EXTRA_STUDENT_ID, -1L);
        if (salonId < 0 || studentId < 0) {
            finish();
            return;
        }
        SalonViewModel vm = new ViewModelProvider(this).get(SalonViewModel.class);
        vm.bindSalon(repo, salonId);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> {
            Intent back = new Intent(this, LoginActivity.class);
            back.putExtra(LoginActivity.EXTRA_MANUAL_LOGIN, true);
            startActivity(back);
            finish();
        });

        repo.findSalonById(salonId, new RepoCallback<Salon>() {
            @Override
            public void onSuccess(Salon salon) {
                toolbar.setTitle("Salón " + salon.number + " · Alumno");
            }

            @Override
            public void onError(String message) {
                Toast.makeText(StudentSalonActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        ViewPager2 pager = findViewById(R.id.pager);
        pager.setOffscreenPageLimit(1);
        pager.setAdapter(new StudentPagerAdapter(this, salonId, studentId));
        TabLayout tabs = findViewById(R.id.tabs);
        String[] titles = {
                getString(R.string.tab_classroom),
                getString(R.string.tab_assignments),
                getString(R.string.tab_my_work),
                getString(R.string.tab_messages)
        };
        new TabLayoutMediator(tabs, pager, (tab, position) -> tab.setText(titles[position])).attach();
    }
}
