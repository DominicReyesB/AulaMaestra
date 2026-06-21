package com.aulamaestra.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.aulamaestra.R;
import com.aulamaestra.api.dto.AuthLoginResponse;
import com.aulamaestra.db.AulaRepository;
import com.aulamaestra.db.RepoCallback;
import com.aulamaestra.db.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {
    public static final String EXTRA_MANUAL_LOGIN = "manual_login";

    private final AulaRepository repo = AulaRepository.get();
    private SessionManager session;
    private TextInputEditText inputUser;
    private TextInputEditText inputPass;
    private MaterialButton btnLogin;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        session.clearAll();
        setContentView(R.layout.activity_login);
        inputUser = findViewById(R.id.inputUser);
        inputPass = findViewById(R.id.inputPass);
        btnLogin = findViewById(R.id.btnLogin);

        findViewById(R.id.btnLogin).setOnClickListener(v -> doLogin());
        findViewById(R.id.btnGoRegister).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void doLogin() {
        String name = textOf(inputUser);
        String pass = textOf(inputPass);
        if (name.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, R.string.login_fill_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);
        repo.login(name, pass, new RepoCallback<AuthLoginResponse>() {
            @Override
            public void onSuccess(AuthLoginResponse r) {
                setLoading(false);
                if ("teacher".equals(r.role) && r.teacherId != null) {
                    session.clearStudent();
                    session.setTeacherId(r.teacherId);
                    startActivity(new Intent(LoginActivity.this, TeacherSalonsActivity.class));
                    finish();
                    return;
                }
                if ("student".equals(r.role) && r.studentId != null && r.salonId != null) {
                    session.clearTeacher();
                    session.saveStudentSession(r.studentId, r.displayName, r.salonId);
                    openStudent(r.studentId, r.salonId, r.displayName);
                    return;
                }
                Toast.makeText(LoginActivity.this, R.string.login_error, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openStudent(long studentId, long salonId, String displayName) {
        session.saveStudentSession(studentId, displayName == null ? session.getStudentName() : displayName, salonId);
        Intent i = new Intent(this, StudentSalonActivity.class);
        i.putExtra(StudentSalonActivity.EXTRA_SALON_ID, salonId);
        i.putExtra(StudentSalonActivity.EXTRA_STUDENT_ID, studentId);
        startActivity(i);
        finish();
    }

    private void setLoading(boolean loading) {
        if (btnLogin != null) {
            btnLogin.setEnabled(!loading);
            btnLogin.setText(loading ? R.string.login_loading : R.string.login);
        }
    }

    private static String textOf(TextInputEditText e) {
        if (e.getText() == null) {
            return "";
        }
        return e.getText().toString().trim();
    }
}
