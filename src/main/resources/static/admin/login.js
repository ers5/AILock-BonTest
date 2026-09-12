const passwordInput = document.getElementById("password");
const message = document.getElementById("message");

passwordInput?.focus();

const query = new URLSearchParams(window.location.search);
if (query.get("error") === "1" && message) {
    message.textContent = "비밀번호가 올바르지 않습니다.";
}
