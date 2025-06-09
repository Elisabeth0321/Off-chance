const BASE_URL = '/api/off-chance';
let currentRelativePath = '';

document.addEventListener('DOMContentLoaded', () => {
    initCurrentPath();
    loadAllFiles();
});

window.addEventListener('popstate', () => {
    initCurrentPath();
    loadAllFiles();
});

function initCurrentPath() {
    const fullPath = window.location.pathname;
    const storagePrefix = '/storage.html';

    currentRelativePath = fullPath.startsWith(storagePrefix)
        ? decodeURIComponent(fullPath.slice(storagePrefix.length))
        : '';
}

function loadAllFiles() {
    fetch(`${BASE_URL}/storage?relativePath=${encodeURIComponent(currentRelativePath)}`)
        .then(response => response.json())
        .then(files => {
            renderFiles(files);
            updateCurrentPathDisplay();
        })
        .catch(error => console.error('Ошибка при загрузке файлов:', error));
}

function renderFiles(files) {
    const fileList = document.getElementById('fileList');
    fileList.innerHTML = '';

    files.forEach(file => {
        const fileDiv = document.createElement('div');
        fileDiv.className = 'file-item';

        const nameSpan = document.createElement('span');
        nameSpan.className = 'file-name';
        nameSpan.textContent = `${file.type === 'FOLDER' ? '📁' : '📄'} ${file.name}`;

        if (file.type === 'FOLDER') {
            nameSpan.style.cursor = 'pointer';
            nameSpan.onclick = () => openFolder(file.id);
        }

        const actionsDiv = document.createElement('div');
        actionsDiv.className = 'file-actions';
        appendFileActions(file, actionsDiv);

        fileDiv.append(nameSpan, actionsDiv);
        fileList.appendChild(fileDiv);
    });
}

function appendFileActions(file, container) {
    if (file.type === 'FILE') {
        container.append(
            createButton('Скачать', () => downloadFile(file.id), '📥'),
            createButton('Удалить', () => deleteFile(file.id), '🗑️')
        );
    } else if (file.type === 'FOLDER') {
        container.append(
            createButton('Скачать ZIP', () => downloadFolder(file.id), '📦'),
            createButton('Удалить', () => deleteFolder(file.id), '🗑️')
        );
    }
}

function updateCurrentPathDisplay() {
    const currentPathSpan = document.getElementById('currentPath');
    currentPathSpan.textContent = "/" + (currentRelativePath ? currentRelativePath.replace(/\\/g, '/') : '');
}

function openFolder(folderId) {
    currentRelativePath = folderId;
    loadAllFiles();
}

function goHome() {
    currentRelativePath = '';
    loadAllFiles();
}

function goBack() {
    if (!currentRelativePath) return;
    const parts = currentRelativePath.split(/[\\\/]/).filter(Boolean);
    parts.pop();
    currentRelativePath = parts.join('/');
    loadAllFiles();
}

function uploadFiles() {
    const input = document.getElementById('fileInput');
    const files = input.files;

    if (!files.length) {
        alert('Выберите файл(ы)');
        return;
    }

    const progressContainer = document.getElementById('uploadProgressContainer');
    progressContainer.innerHTML = '';

    Array.from(files).forEach(file => {
        const progressId = `progress-${file.name.replace(/\W/g, '')}`;
        const uploadItem = createProgressItem(file.name, progressId);
        progressContainer.appendChild(uploadItem);

        const formData = new FormData();
        formData.append('file', file);
        formData.append('folderId', currentRelativePath);

        const xhr = new XMLHttpRequest();
        xhr.open('POST', `${BASE_URL}/upload`, true);

        xhr.upload.onprogress = event => updateProgress(event, progressId);
        xhr.onload = () => handleUploadComplete(xhr, progressId);
        xhr.onerror = () => alert(`Ошибка сети при загрузке файла ${file.name}`);
        xhr.send(formData);
    });

    input.value = '';
}

function uploadFolder() {
    const input = document.getElementById('folderInput');
    const files = input.files;

    if (!files.length) {
        alert('Выберите папку');
        return;
    }

    const progressContainer = document.getElementById('uploadProgressContainer');
    progressContainer.innerHTML = '';

    const formData = new FormData();
    Array.from(files).forEach((file, idx) => {
        formData.append('files', file);
        formData.append('relativePaths', file.webkitRelativePath);
        const uploadItem = createProgressItem(file.webkitRelativePath, `progress-folder-${idx}`);
        progressContainer.appendChild(uploadItem);
    });

    formData.append('parentFolderId', currentRelativePath);

    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${BASE_URL}/upload-folder`, true);

    xhr.upload.onprogress = event => {
        if (event.lengthComputable) {
            const percent = (event.loaded / event.total) * 100;
            Array.from(files).forEach((_, idx) =>
                updateProgressBar(`progress-folder-${idx}`, percent)
            );
        }
    };

    xhr.onload = () => {
        if (xhr.status === 200 || xhr.status === 201) {
            setTimeout(() => {
                progressContainer.innerHTML = '';
                loadAllFiles();
            }, 500);
        } else {
            alert('Ошибка загрузки папки');
        }
    };

    xhr.onerror = () => alert('Ошибка сети при загрузке папки');
    xhr.send(formData);
    input.value = '';
}

function createFolder() {
    const folderNameInput = document.getElementById('folderName');
    const folderName = folderNameInput.value.trim();

    if (!folderName) {
        alert('Введите имя папки');
        return;
    }

    fetch(`${BASE_URL}/create-folder?name=${encodeURIComponent(folderName)}&parentFolderId=${encodeURIComponent(currentRelativePath)}`, {
        method: 'POST'
    })
        .then(response => {
            if (response.ok) {
                loadAllFiles();
                folderNameInput.value = '';
            } else {
                alert('Ошибка создания папки.');
            }
        })
        .catch(error => console.error('Ошибка:', error));
}

function deleteFile(filePath) {
    if (!confirm('Удалить файл?')) return;

    fetch(`${BASE_URL}/delete-file?filePath=${encodeURIComponent(filePath)}`, {
        method: 'DELETE'
    })
        .then(response => {
            if (response.ok) {
                loadAllFiles();
            } else {
                alert('Ошибка удаления файла.');
            }
        })
        .catch(error => console.error('Ошибка:', error));
}

function deleteFolder(folderId) {
    if (!confirm('Удалить папку и всё её содержимое?')) return;

    fetch(`${BASE_URL}/delete-folder/${encodeURIComponent(folderId)}`, {
        method: 'DELETE'
    })
        .then(response => {
            if (response.ok) {
                loadAllFiles();
            } else {
                alert('Ошибка удаления папки.');
            }
        })
        .catch(error => console.error('Ошибка:', error));
}

function downloadFile(fileId) {
    window.location.href = `${BASE_URL}/download/${encodeURIComponent(fileId)}`;
}

function downloadFolder(folderId) {
    window.location.href = `${BASE_URL}/download-zip/${encodeURIComponent(folderId)}`;
}

function searchFiles() {
    const query = document.getElementById('searchQuery').value.trim();
    if (!query) {
        alert('Введите поисковый запрос');
        return;
    }

    fetch(`${BASE_URL}/search?query=${encodeURIComponent(query)}`)
        .then(response => response.json())
        .then(files => renderFiles(files))
        .catch(error => console.error('Ошибка поиска:', error));
}

function createButton(text, onClick, icon = '') {
    const button = document.createElement('button');
    button.innerHTML = icon ? `${icon} ${text}` : text;
    button.onclick = onClick;
    return button;
}

function createProgressItem(label, progressId) {
    const uploadItem = document.createElement('div');
    uploadItem.className = 'upload-item';
    uploadItem.innerHTML = `
        <div>${label}</div>
        <div class="progress-bar">
            <div class="progress-bar-fill" id="${progressId}"></div>
        </div>
    `;
    return uploadItem;
}

function updateProgress(event, progressId) {
    if (event.lengthComputable) {
        const percent = (event.loaded / event.total) * 100;
        updateProgressBar(progressId, percent);
    }
}

function updateProgressBar(progressId, percent) {
    const progressFill = document.getElementById(progressId);
    if (progressFill) {
        progressFill.style.width = `${percent.toFixed(0)}%`;
        progressFill.textContent = `${percent.toFixed(0)}%`;
    }
}

function handleUploadComplete(xhr, progressId) {
    const progressFill = document.getElementById(progressId);
    if (xhr.status === 200 || xhr.status === 201) {
        setTimeout(() => {
            const uploadItem = progressFill?.closest('.upload-item');
            if (uploadItem) uploadItem.remove();
            loadAllFiles();
        }, 300);
    } else {
        alert('Ошибка загрузки файла');
    }
}
