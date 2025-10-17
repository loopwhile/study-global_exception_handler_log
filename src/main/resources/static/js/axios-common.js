const api = axios.create({
  headers: { 'X-Requested-With': 'XMLHttpRequest' }
});

api.interceptors.response.use(
  res => res,
  err => {
    const res = err.response;
    const data = res?.data;

    // 상세 에러 구조 파싱
    let msg = '';

    if (!res) {
      msg = '서버로부터 응답이 없습니다.';
    } else if (data) {
      // 1️⃣ 가장 우선: message
      msg = data.message || '';

      // 2️⃣ 필드 오류가 있으면 필드별 reason도 추가
      if (data.errors && data.errors.length > 0) {
        const detail = data.errors
          .map(e => `- ${e.field}: ${e.reason}`)
          .join('\n');
        msg += `\n\n상세 오류:\n${detail}`;
      }

      // 3️⃣ 디버깅용 코드, 상태 표시 (선택)
      msg += `\n\n[${data.code || 'NO_CODE'}] HTTP ${data.status || res.status}`;

      // 4️⃣ 경로 정보 (선택)
      if (data.path) msg += `\n요청 경로: ${data.path}`;
    } else {
      msg = `요청 처리 중 오류가 발생했습니다. (HTTP ${res?.status})`;
    }

    alert(msg.trim());

    // 뒤로가기 정책
    const shouldGoBack =
      res?.config?.headers?.['X-Go-Back'] === 'true' ||
      (res?.status >= 400 && res?.status < 500);

    if (shouldGoBack && document.referrer) {
      history.back();
    }

    return Promise.reject(err);
  }
);
