// Confirmação simples antes de enviar um formulário, sem JavaScript inline (bloqueado pela
// Content-Security-Policy). Uso: <form data-confirmar="Excluir este paciente?">.
document.querySelectorAll('form[data-confirmar]').forEach(form => {
    form.addEventListener('submit', evento => {
        if (!window.confirm(form.dataset.confirmar)) evento.preventDefault();
    });
});
