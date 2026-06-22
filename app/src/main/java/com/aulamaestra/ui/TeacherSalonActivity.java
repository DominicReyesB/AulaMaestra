package com.aulamaestra.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
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
    private static final String TIKTOK_URL = "https://www.tiktok.com/@app_humanidade_cl?_r=1&_t=ZS-97JrK3R7Nbh";
    private static final String INSTAGRAM_URL = "https://www.instagram.com/app_humanidades_class?igsh=MTF2bmY2c2VncGxzZA==";

    private final AulaRepository repo = AulaRepository.get();
    private ActivityResultLauncher<String> pickFile;
    private Uri pendingPostUri;
    private TextView activePostFileLabel;
    private SalonViewModel salonViewModel;
    private boolean hasResumed;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_salon);
        long salonId = getIntent().getLongExtra(EXTRA_SALON_ID, -1L);
        if (salonId < 0) {
            finish();
            return;
        }
        salonViewModel = new ViewModelProvider(this).get(SalonViewModel.class);
        salonViewModel.bindSalon(repo, salonId);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        addToolbarActions(toolbar);

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
        pager.setOffscreenPageLimit(1);
        pager.setAdapter(new TeacherPagerAdapter(this, salonId));
        TabLayout tabs = findViewById(R.id.tabs);
        String[] titles = {
                getString(R.string.tab_announcements),
                getString(R.string.tab_assignments),
                getString(R.string.tab_roster),
                getString(R.string.tab_grades),
                getString(R.string.tab_messages)
        };
        new TabLayoutMediator(tabs, pager, (tab, position) -> tab.setText(titles[position])).attach();

        FloatingActionButton fab = findViewById(R.id.fabPublish);
        fab.setOnClickListener(v -> showNewPostDialog(salonId, salonViewModel));
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                fab.setVisibility(position <= 1 ? View.VISIBLE : View.GONE);
                if (position <= 1 && hasResumed) {
                    salonViewModel.refreshPostsIfStale(repo);
                }
            }
        });
        fab.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasResumed && salonViewModel != null) {
            salonViewModel.refreshPostsIfStale(repo);
        }
        hasResumed = true;
    }

    private void showNewPostDialog(long salonId, SalonViewModel vm) {
        pendingPostUri = null;
        View form = getLayoutInflater().inflate(R.layout.dialog_new_publication, null);
        MaterialButtonToggleGroup typeGroup = form.findViewById(R.id.typeGroup);
        typeGroup.check(R.id.typeAnnouncement);
        View typeFile = form.findViewById(R.id.typeFile);
        if (typeFile != null) {
            typeFile.setVisibility(View.GONE);
        }
        TextInputEditText title = form.findViewById(R.id.inputTitle);
        TextInputEditText body = form.findViewById(R.id.inputBody);
        TextInputEditText link = form.findViewById(R.id.inputPostLink);
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
            postType = checked == R.id.typeAssignment ? PostType.ASSIGNMENT : PostType.ANNOUNCEMENT;
            String t = textOf(title);
            if (TextUtils.isEmpty(t)) {
                Toast.makeText(this, "El título es obligatorio", Toast.LENGTH_SHORT).show();
                return;
            }
            String b = textOf(body);
            String rawLink = textOf(link);
            String linkUrl = ExternalLinkUtils.normalizeWebUrl(rawLink);
            if (!rawLink.isEmpty() && linkUrl == null) {
                Toast.makeText(this, R.string.invalid_link, Toast.LENGTH_SHORT).show();
                return;
            }
            final int finalPostType = postType;
            setPublishBusy(d, true);
            if (pendingPostUri != null) {
                Uri uri = pendingPostUri;
                IoUtils.copyUriToFilesDirAsync(this, uri, "pub-" + System.currentTimeMillis(), local -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (local == null) {
                        setPublishBusy(d, false);
                        Toast.makeText(this, "No se pudo leer el archivo", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    repo.uploadLocalFile(local, new RepoCallback<String>() {
                        @Override
                        public void onSuccess(String url) {
                            publishPost(salonId, finalPostType, t, b, url, linkUrl, vm, d);
                        }

                        @Override
                        public void onError(String message) {
                            setPublishBusy(d, false);
                            Toast.makeText(TeacherSalonActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            } else {
                publishPost(salonId, finalPostType, t, b, null, linkUrl, vm, d);
            }
        });
    }

    private void publishPost(long salonId, int postType, String title, String body, String fileUrl,
                             String linkUrl, SalonViewModel vm, AlertDialog d) {
        repo.insertPost(salonId, postType, title, body, fileUrl, linkUrl, new RepoCallback<Long>() {
            @Override
            public void onSuccess(Long id) {
                vm.addPost(new com.aulamaestra.model.Post(
                        id, postType, title, body, fileUrl, linkUrl, System.currentTimeMillis()));
                vm.bump(repo);
                Toast.makeText(TeacherSalonActivity.this, "Publicado", Toast.LENGTH_SHORT).show();
                d.dismiss();
            }

            @Override
            public void onError(String message) {
                setPublishBusy(d, false);
                Toast.makeText(TeacherSalonActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setPublishBusy(AlertDialog dialog, boolean busy) {
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(
                busy ? R.string.publishing : R.string.save);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!busy);
    }

    private static String textOf(TextInputEditText e) {
        if (e.getText() == null) {
            return "";
        }
        return e.getText().toString().trim();
    }

    private void addToolbarActions(MaterialToolbar toolbar) {
        toolbar.getMenu().add(Menu.NONE, 1, 1, "Ayuda")
                .setIcon(tintedIcon(android.R.drawable.ic_menu_help))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.getMenu().add(Menu.NONE, 2, 2, "Redes sociales")
                .setIcon(tintedIcon(android.R.drawable.ic_menu_share))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showHelpDialog();
                return true;
            }
            if (item.getItemId() == 2) {
                showSocialDialog();
                return true;
            }
            return false;
        });
    }

    private android.graphics.drawable.Drawable tintedIcon(int resourceId) {
        android.graphics.drawable.Drawable icon = AppCompatResources.getDrawable(this, resourceId);
        if (icon != null) {
            icon = DrawableCompat.wrap(icon).mutate();
            DrawableCompat.setTint(icon, ContextCompat.getColor(this, R.color.on_primary));
        }
        return icon;
    }

    private void showHelpDialog() {
        repo.isAiHelpAvailable(new RepoCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean available) {
                showHelpOptions(Boolean.TRUE.equals(available));
            }

            @Override
            public void onError(String message) {
                showHelpOptions(false);
            }
        });
    }

    private void showHelpOptions(boolean aiAvailable) {
        String[] questions = {
                "¿Cómo publico una tarea?",
                "¿Cómo publico un anuncio?",
                "¿Cómo abro un archivo adjunto?",
                "¿Cómo elimino una publicación?",
                "¿Cómo califico una entrega?",
                "¿Qué calificación puedo poner?",
                "¿Dónde veo los archivos enviados?",
                "¿Cómo elimino a un alumno?",
                "¿Cómo envío un mensaje?",
                "¿Qué hago si la app tarda en cargar?"
        };
        String[] answers = {
                "Toca el botón +, elige Tarea, escribe título y descripción, agrega archivo si lo necesitas y guarda.",
                "Toca el botón +, deja seleccionada la opción Anuncio, completa la información y presiona Guardar.",
                "Toca el nombre del archivo dentro del anuncio o tarea. Se abrirá con una aplicación compatible.",
                "En Anuncios o Tareas, toca el icono de papelera y confirma. También se eliminarán las entregas relacionadas.",
                "Entra a Calificar, toca la entrega del alumno, revisa el adjunto y presiona Calificar.",
                "Solo se permiten calificaciones de 0 a 10. La app no guardará números fuera de ese rango.",
                "En Calificar, toca la entrega y usa Ver adjunto para abrir el enlace o archivo que envió el alumno.",
                "Entra a Alumnos, toca la papelera junto al nombre y confirma. Se borrarán también sus entregas y mensajes.",
                "Entra a Mensajes, elige al alumno, escribe el mensaje y presiona Enviar.",
                "Comprueba tu conexión y espera unos segundos. Si continúa, cierra y abre la app; tus publicaciones guardadas no se pierden."
        };
        String[] options = questions;
        if (aiAvailable) {
            options = new String[questions.length + 1];
            options[0] = getString(R.string.ai_help_option);
            System.arraycopy(questions, 0, options, 1, questions.length);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Ayuda")
                .setItems(options, (dialog, which) -> {
                    if (aiAvailable && which == 0) {
                        AiHelpDialog.show(this);
                        return;
                    }
                    int index = aiAvailable ? which - 1 : which;
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(questions[index])
                            .setMessage(answers[index])
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                })
                .show();
    }

    private void showSocialDialog() {
        String[] networks = {"TikTok", "Instagram"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Redes sociales")
                .setItems(networks, (dialog, which) -> openUrl(which == 0 ? TIKTOK_URL : INSTAGRAM_URL))
                .show();
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
