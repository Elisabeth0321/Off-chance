async function checkAuth() {
    try {
        const response = await fetch('/api/users/me', { credentials: 'include' });
        if (!response.ok) throw new Error("Unauthorized");

        const user = await response.json();
        renderAuthenticatedUser(user);
        renderStorageAccess(true);
    } catch {
        renderGuestUser();
        renderStorageAccess(false);
    }
}

function renderAuthenticatedUser(user) {
    const nav = document.getElementById('user-nav');
    nav.innerHTML = `
        <span class="user-label">Здравствуйте, ${user.name}</span>
        <a href="/account.html" class="btn-nav">Личный кабинет</a>
        <a href="#" class="btn-nav btn-logout">Выйти</a>
    `;
}

function renderGuestUser() {
    const nav = document.getElementById('user-nav');
    nav.innerHTML = `
        <a href="/authorization_form.html" class="btn-nav">Войти</a>
        <a href="/registration_form.html" class="btn-nav">Регистрация</a>
    `;
}

function renderStorageAccess(isAuthenticated) {
    const container = document.getElementById('storage-access');

    if (isAuthenticated) {
        container.innerHTML = `
            <a href="/storage.html" class="main-button">Перейти к резервному хранилищу</a>
        `;
    } else {
        container.innerHTML = `
            <p class="warning-text">Пока вы не авторизованы, у вас нет доступа к резервному хранилищу</p>
        `;
    }
}

async function logout() {
    try {
        const response = await fetch('/api/users/logout', {
            method: 'DELETE',
            credentials: 'include'
        });

        if (response.ok) {
            window.location.href = "/";
        } else {
            console.error("Ошибка при выходе:", await response.text());
        }
    } catch (e) {
        console.error("Сетевая ошибка:", e);
    }
}

function setupEventDelegation() {
    document.addEventListener("click", function(event) {
        if (event.target.matches(".btn-logout")) {
            event.preventDefault();
            logout();
        }
    });
}

document.addEventListener("DOMContentLoaded", () => {
    setupEventDelegation();
    checkAuth();
});
