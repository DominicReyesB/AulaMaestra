package com.aulamaestra.ui;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.aulamaestra.R;
import com.aulamaestra.db.AulaRepository;
import com.aulamaestra.db.RepoCallback;
import com.aulamaestra.model.PostType;
import com.aulamaestra.model.Salon;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.TextInputEditText;

public class TeacherSalonActivity extends AppCompatActivity {
    public static final String EXTRA_SALON_ID = "salon_id";

    private final AulaRepository repo = AulaRepository.get();
    private ActivityResultLauncher<String> pickFile;
    private Uri pendingPostUri;
    private TextView activePostFileLabel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_salon);
        long salonId = getIntent().getLongExtra(EXTRA_SALON_ID, -1L);
        if (salonId < 0) {
            finish();
            return;
        }
        SalonViewModel vm = new ViewModelProvider(this).get(SalonViewModel.class);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        repo.findSalonById(salonId, new RepoCallback<Salon>() {
            @Override
            public void onSuccess(Salon salon) {
                toolbar.setTitle("Salón " + salon.number);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(TeacherSalonActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        pickFile = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            pendingPostUri = uri;
            if (activePostFileLabel != null) {
                activePostFileLabel.setText(uri == null ? "" : "Archivo seleccionado");
            }
        });

        ViewPager2 pager = findViewById(R.id.pager);
        pager.setAdapter(new TeacherPagerAdapter(this, salonId));
        TabLayout tabs = findViewById(R.id.tabs);
        String[] titles = {
                getString(R.string.tab_announcements),
                getString(R.string.tab_assignments),
                getString(R.string.tab_files),
                getString(R.string.tab_roster),
                getString(R.string.tab_grades),
                getString(R.string.tab_messages)
        };
        new TabLayoutMediator(tabs, pager, (tab, position) -> tab.setText(titles[position])).attach();

        FloatingActionButton fab = findViewById(R.id.fabPublish);
        fab.setOnClickListener(v -> showNewPostDialog(salonId, vm));
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                fab.setVisibility(position <= 2 ? View.VISIBLE : View.GONE);
            }
        });
        fab.setVisibility(View.VISIBLE);
    }

    private void showNewPostDialog(long salonId, SalonViewModel vm) {
        pendingPostUri = null;
        View form = getLayoutInflater().inflate(R.layout.dialog_new_publication, null);
        MaterialButtonToggleGroup typeGroup = form.findViewById(R.id.typeGroup);
        typeGroup.check(R.id.typeAnnouncement);
        TextInputEditText title = form.findViewById(R.id.inputTitle);
        TextInputEditText body = form.findViewById(R.id.inputBody);
        TextView picked = form.findViewById(R.id.textPickedFile);
        form.findViewById(R.id.btnPickFile).setOnClickListener(v -> {
            activePostFileLabel = picked;
            pickFile.launch("*/*");
        });

        AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.new_post)
                .setView(form)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int checked = typeGroup.getCheckedButtonId();
            int postType;
            if (checked == R.id.typeAssignment) {
                postType = PostType.ASSIGNMENT;
            } else if (checked == R.id.typeFile) {
                postType = PostType.FILE;
            } else {
                postType = PostType.ANNOUNCEMENT;
            }
            String t = textOf(title);
            if (TextUtils.isEmpty(t)) {
                Toast.makeText(this, "El título es obligatorio", Toast.LENGTH_SHORT).show();
                return;
            }
            if (postType == PostType.FILE && pendingPostUri == null) {
                Toast.makeText(this, "Selecciona un archivo", Toast.LENGTH_SHORT).show();
                return;
            }
            String b = textOf(body);
            final int finalPostType = postType;
            if (pendingPostUri != null) {
                String local = IoUtils.copyUriToFilesDir(this, pendingPostUri, "pub-" + System.currentTimeMillis());
                if (local == null) {
                    Toast.makeText(this, "No se pudo leer el archivo", Toast.LENGTH_SHORT).show();
                    return;
                }
                repo.uploadLocalFile(local, new RepoCallback<String>() {
                    @Override
                    public void onSuccess(String url) {
                        publishPost(salonId, finalPostType, t, b, url, vm, d);
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(TeacherSalonActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                publishPost(salonId, finalPostType, t, b, null, vm, d);
            }
        });
    }

    private void publishPost(long salonId, int postType, String title, String body, String fileUrl,
                           SalonViewModel vm, AlertDialog d) {
        repo.insertPost(salonId, postType, title, body, fileUrl, new RepoCallback<Long>() {
            @Override
            public void onSuccess(Long id) {
                vm.bump();
                Toast.makeText(TeacherSalonActivity.this, "Publicado", Toast.LENGTH_SHORT).show();
                d.dismiss();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(TeacherSalonActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static String textOf(TextInputEditText e) {
        if (e.getText() == null) {
            return "";
        }
        return e.getText().toString().trim();
    }
}
