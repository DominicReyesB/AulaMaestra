package com.aulamaestra.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
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
    private static final String TIKTOK_URL = "https://www.tiktok.com/@app_humanidade_cl?_r=1&_t=ZS-97JrK3R7Nbh";
    private static final String INSTAGRAM_URL = "https://www.instagram.com/app_humanidades_class?igsh=MTF2bmY2c2VncGxzZA==";

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
        addToolbarActions(toolbar);

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
                getString(R.string.tab_pending),
                getString(R.string.tab_delivered),
                getString(R.string.tab_messages)
        };
        new TabLayoutMediator(tabs, pager, (tab, position) -> tab.setText(titles[position])).attach();
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
        String[] questions = {
                "¿Cómo entrego una tarea?",
                "¿Puedo entregar dos veces la misma tarea?",
                "¿Cómo elimino una entrega equivocada?",
                "¿Dónde veo mis tareas pendientes?",
                "¿Dónde veo mis tareas entregadas?",
                "¿Dónde veo mi calificación?",
                "¿Cómo abro un archivo de la maestra?",
                "¿Qué hago si no puedo adjuntar un archivo?",
                "¿Cómo envío un mensaje a la maestra?",
                "¿Por qué no me pide el código al iniciar sesión?",
                "¿Puedo estar en más de un salón?",
                "¿Qué hago si la app tarda en cargar?"
        };
        String[] answers = {
                "Entra a Pendientes, toca Entregar tarea, agrega tu respuesta, enlace o archivo y confirma.",
                "Solo puede haber una entrega activa. Si te equivocaste, elimínala desde Entregadas y vuelve a enviarla desde Pendientes.",
                "Entra a Entregadas, toca la papelera de la tarea y confirma. Después aparecerá otra vez en Pendientes.",
                "En la pestaña Pendientes aparecen solamente las tareas que todavía no has enviado.",
                "En la pestaña Entregadas aparecen tus respuestas, adjuntos, fecha de envío y calificación.",
                "Entra a Entregadas. Debajo de cada tarea aparece la calificación o el aviso Pendiente de calificación.",
                "Toca el nombre del archivo dentro del anuncio o tarea para abrirlo con una aplicación compatible.",
                "Revisa tu conexión y prueba con otro archivo. También puedes pegar un enlace de Drive, YouTube u otra plataforma.",
                "Entra a Mensajes, escribe tu mensaje y presiona Enviar.",
                "El código se usa una sola vez al registrarte. Después solo necesitas tu nombre y contraseña.",
                "No. Tu cuenta pertenece a un solo salón para evitar confusiones con tareas y calificaciones.",
                "Comprueba tu conexión y espera unos segundos. Si continúa, cierra y abre la app e inicia sesión nuevamente."
        };
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Ayuda")
                .setItems(questions, (dialog, which) -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(questions[which])
                        .setMessage(answers[which])
                        .setPositiveButton(android.R.string.ok, null)
                        .show())
                .show();
    }

    private void showSocialDialog() {
        String[] networks = {"TikTok", "Instagram"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Redes sociales")
                .setItems(networks, (dialog, which) -> openUrl(which == 0 ? TIKTOK_URL : INSTAGRAM_URL))
                .show();
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
