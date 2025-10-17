// ✅ 이렇게만 (script 태그 없이)
const api = axios.create({
  headers: { 'X-Requested-With': 'XMLHttpRequest' }
});

api.interceptors.response.use(
  res => res,
  err => {
    const res = err.response;
    const data = res?.data;
    const msg =
      data?.message ||
      (data?.errors && data.errors.length ? data.errors[0].reason : null) ||
      '요청 처리 중 오류가 발생했습니다.';

    alert(msg);

    const shouldGoBack =
      res?.config?.headers?.['X-Go-Back'] === 'true' ||
      (res?.status >= 400 && res?.status < 500);

    if (shouldGoBack && document.referrer) {
      history.back();
    }
    return Promise.reject(err);
  }
);
