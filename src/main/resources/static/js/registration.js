import {hashPassword} from "./hash.js";

class RegistrationForm {
    constructor(formId, errorContainerId) {
        this.form = document.getElementById(formId);
        this.errorBox = document.getElementById(errorContainerId);
        this.loader = document.getElementById('loading-indicator');
        this.init();
    }

    init() {
        if (!this.form) {
            console.error(`Форма с id "${formId}" не найдена.`);
            return;
        }
        this.form.addEventListener('submit', (e) => this.handleSubmit(e));
    }

    async handleSubmit(e) {
        e.preventDefault();
        this.clearError();
        this.showLoader();

        const username = this.getValue('username');
        const email = this.getValue('email');
        const password = this.getValue('password');

        if (!username || !email || !password) {
            this.hideLoader();
            this.showError('Все поля обязательны для заполнения.');
            return;
        }

        const hashedPassword = await hashPassword(password);

        try {
            const payload = {
                username,
                email,
                password: hashedPassword
            };

            const response = await fetch('/api/users/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify(payload)
            });

            const data = await response.json();
            this.hideLoader();

            if (!response.ok) {
                this.showError(data.message || 'Ошибка при регистрации.');
            } else {
                window.location.href = '/';
            }
        } catch (err) {
            this.hideLoader();
            this.showError('Не удалось подключиться к серверу.');
            console.error(err);
        }
    }

    getValue(id) {
        return document.getElementById(id)?.value.trim() || '';
    }

    showError(message) {
        this.errorBox.textContent = message;
        this.errorBox.classList.add('visible');
    }

    clearError() {
        this.errorBox.textContent = '';
        this.errorBox.classList.remove('visible');
    }

    showLoader() {
        this.loader?.classList.add('visible');
    }

    hideLoader() {
        this.loader?.classList.remove('visible');
    }
}

export default RegistrationForm;
