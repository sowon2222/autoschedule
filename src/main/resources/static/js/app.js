// Simple auth & API helper used by static pages
(function () {
  function getAccessToken() {
    return localStorage.getItem('accessToken') || '';
  }

  function getRefreshToken() {
    return localStorage.getItem('refreshToken') || '';
  }

  async function refreshIfNeeded(response) {
    if (response.status !== 401) return null;
    const refreshToken = getRefreshToken();
    if (!refreshToken) return null;
    const res = await fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + refreshToken },
    });
    if (!res.ok) return null;
    const data = await res.json();
    localStorage.setItem('accessToken', data.accessToken);
    localStorage.setItem('refreshToken', data.refreshToken);
    return data.accessToken;
  }

  async function apiFetch(url, options = {}) {
    const headers = Object.assign({}, options.headers || {});
    const token = getAccessToken();
    if (token) headers.Authorization = 'Bearer ' + token;
    const first = await fetch(url, { ...options, headers });
    if (first.status !== 401) return first;
    const newToken = await refreshIfNeeded(first);
    if (!newToken) return first;
    const headers2 = Object.assign({}, options.headers || {}, {
      Authorization: 'Bearer ' + newToken,
    });
    return fetch(url, { ...options, headers: headers2 });
  }

  function isLoggedIn() {
    return !!getAccessToken();
  }

  function logout() {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('userEmail');
    localStorage.removeItem('userName');
    location.href = '/';
  }

  window.App = { apiFetch, isLoggedIn, logout };
})();


