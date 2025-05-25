import {hashPassword} from "./hash";

export default class UpdateUser {
    constructor(formId, statusElementId, deleteButtonId) {
        this.form = document.getElementById(formId);
        this.status = document.getElementById(statusElementId);
        this.deleteButton = document.getElementById(deleteButtonId);

        this.form.addEventListener("submit", this.handleSubmit.bind(this));
        this.deleteButton.addEventListener("click", this.handleDelete.bind(this));

        this.prefillUserInfo();
    }

    async prefillUserInfo() {
        try {
            const response = await fetch('/api/users/me', {
                credentials: 'include'
            });

            if (response.ok) {
                const user = await response.json();
                this.form.username.value = user.name || '';
            }
        } catch (e) {
            console.error("Не удалось загрузить данные пользователя", e);
        }
    }

    async handleSubmit(event) {
        event.preventDefault();
        const username = this.form.username.value.trim();
        const password = this.form.password.value.trim();

        if (!username && !password) {
            this.showStatus("Введите хотя бы одно поле для обновления", true);
            return;
        }

        const hashedPassword = await hashPassword(password);

        try {
            const payload = {
                username,
                password: hashedPassword
            };

            const response = await fetch('/api/users/update', {
                method: 'PUT',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            });

            if (response.ok) {
                this.showStatus("Данные успешно обновлены!");
                this.form.password.value = "";
            } else {
                const errorText = await response.text();
                this.showStatus(`Ошибка: ${errorText}`, true);
            }
        } catch (e) {
            console.error("Ошибка при обновлении пользователя", e);
            this.showStatus("Ошибка сети. Попробуйте позже.", true);
        }
    }

    async handleDelete() {
        if (!confirm("Вы уверены, что хотите удалить аккаунт? Это действие необратимо.")) {
            return;
        }

        try {
            const response = await fetch("/api/users/delete", {
                method: "DELETE",
                credentials: "include"
            });

            if (response.ok) {
                alert("Аккаунт удалён.");
                window.location.href = "/";
            } else {
                const error = await response.text();
                this.showStatus(`Ошибка: ${error}`, true);
            }
        } catch (e) {
            console.error("Ошибка при удалении аккаунта", e);
            this.showStatus("Сетевая ошибка при удалении аккаунта.", true);
        }
    }

    showStatus(message, isError = false) {
        this.status.textContent = message;
        this.status.style.color = isError ? 'red' : 'green';
    }
}

document.addEventListener("DOMContentLoaded", () => {
    new UpdateUser('account-form', 'status-message', 'delete-account-btn');
});
