// Modais de confirmação compartilhados por uma tabela inteira (um modal por ação, não por linha).
// O botão que abre o modal informa para onde enviar e o que está sendo confirmado:
//   <button data-bs-toggle="modal" data-bs-target="#modalCancelar"
//           data-acao="/agendamentos/cancelar/5" data-descricao="Ana Souza, 20/11 às 09:00">
// O modal precisa do atributo data-confirmacao, de um <form> (com o token CSRF) e,
// opcionalmente, de um elemento [data-descricao] para o texto.

document.querySelectorAll('.modal[data-confirmacao]').forEach(modal => {
    modal.addEventListener('show.bs.modal', evento => {
        const botao = evento.relatedTarget;
        if (!botao) return;

        const form = modal.querySelector('form');
        form.reset(); // limpa o que foi digitado (ex.: motivo) para outro agendamento
        form.action = botao.dataset.acao;

        const descricao = modal.querySelector('[data-descricao]');
        if (descricao) descricao.textContent = botao.dataset.descricao || '';
    });
});
