// Máscaras de digitação: só formatam o texto enquanto o usuário digita.
// Não validam nada — quem valida e garante o formato final é o back-end
// (setCpf em Paciente e setCrm em Medico). Uso: <input data-mascara="cpf">.

const mascaras = {

    // 12345678901 -> 123.456.789-01
    cpf(valor) {
        const digitos = valor.replace(/\D/g, '').slice(0, 11);

        let texto = digitos.slice(0, 3);
        if (digitos.length > 3) texto += '.' + digitos.slice(3, 6);
        if (digitos.length > 6) texto += '.' + digitos.slice(6, 9);
        if (digitos.length > 9) texto += '-' + digitos.slice(9);
        return texto;
    },

    // to1001, 1001to, 1001/TO -> CRM-TO 1001
    crm(valor) {
        // Descarta o "CRM" que a própria máscara escreveu (ou que o usuário digitou).
        let letras = valor.toUpperCase().replace(/[^A-Z]/g, '');
        while (letras.startsWith('CRM')) letras = letras.slice(3);

        const uf = letras.slice(0, 2);
        const numero = valor.replace(/\D/g, '').slice(0, 6);

        if (!uf) return numero;
        return 'CRM-' + uf + (numero ? ' ' + numero : '');
    }
};

document.querySelectorAll('[data-mascara]').forEach(campo => {
    const formatar = mascaras[campo.dataset.mascara];
    const aplicar = () => {
        const texto = formatar(campo.value);
        if (texto !== campo.value) campo.value = texto;
    };

    campo.addEventListener('input', aplicar);
    aplicar();
});
