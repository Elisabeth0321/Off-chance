class LoginForm {
    constructor(formId, errorContainerId) {
        this.form = document.getElementById(formId);
        this.errorBox = document.getElementById(errorContainerId);
        this.init();
    }

    init() {
        this.form.addEventListener('submit', (e) => this.handleSubmit(e));
    }

    async handleSubmit(e) {
        e.preventDefault();
        this.errorBox.textContent = '';

        const loader = document.getElementById('loading-indicator');
        loader.classList.add('visible');

        const email = this.getValue('email');
        const password = this.getValue('password');
        const rememberMe = !!document.getElementById('rememberMe').checked;

        if (!email || !password) {
            loader.classList.remove('visible');
            this.showError('Все поля обязательны для заполнения.');
            return;
        }

        const payload = {
            email,
            password,
            rememberMe
        };

        try {
            const response = await fetch('/api/users/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify(payload)
            });

            let data = {};
            try {
                data = await response.json();
            } catch (_) {
                data = { message: 'Некорректный ответ от сервера.' };
            }

            loader.classList.remove('visible');

            if (!response.ok) {
                this.showError(data.message || 'Произошла ошибка.');
            } else {
                window.location.href = '/';
            }
        } catch (err) {
            loader.classList.remove('visible');
            this.showError('Ошибка соединения с сервером.');
        }
    }

    getValue(id) {
        return document.getElementById(id)?.value.trim() || '';
    }

    showError(message) {
        this.errorBox.textContent = message;
    }
}

export default LoginForm;
