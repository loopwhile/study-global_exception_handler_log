const api = axios.create({
  headers: { 'X-Requested-With': 'XMLHttpRequest' }
});

api.interceptors.response.use(
  res => res,
  err => {
    const res = err.response;
    const rid = res?.headers?.['x-request-id'];
    let msg = '요청 처리 중 오류가 발생했습니다.';

    if (!res) {
      msg = '서버로부터 응답이 없습니다.';
    } else if (res.data) {
      const data = res.data;
      msg = data.message || msg;
      if (Array.isArray(data.errors) && data.errors.length) {
        msg += `\n\n상세 오류:\n` + data.errors.map(e => `- ${e.field}: ${e.reason}`).join('\n');
      }
      msg += `\n\n[${data.code || 'NO_CODE'}] HTTP ${data.status || res.status}`;
      if (data.path) msg += `\n경로: ${data.path}`;
    } else {
      msg += ` (HTTP ${res.status})`;
    }
    if (rid) msg += `\n요청 ID: ${rid}`;

    if (res?.status === 401) {
      alert('로그인이 필요합니다.\n' + msg);
      location.href = '/login?redirect=' + encodeURIComponent(location.pathname + location.search);
      return Promise.reject(err);
    }
    if (res?.status === 403) {
      alert('접근 권한이 없습니다.\n' + msg);
      return Promise.reject(err);
    }

    alert(msg.trim());

    const shouldGoBack =
      res?.config?.headers?.['X-Go-Back'] === 'true' ||
      (res?.status >= 400 && res?.status < 500);

    if (shouldGoBack && document.referrer) history.back();
    return Promise.reject(err);
  }
);
