export function createGeneration(providers) {
  const template = document.getElementById('generation-fields');
  const dialog = document.getElementById('generation-dialog');
  const form = dialog.querySelector('form');
  function fields(container, previous = {}) {
    container.replaceChildren(template.content.cloneNode(true));
    const model = container.querySelector('[name=provider]');
    const choices = providers();
    model.replaceChildren(...choices.map(choice => new Option(choice.label + ' · ' + choice.model, choice.id)));
    if (choices.some(choice => choice.id === previous.provider)) model.value = previous.provider;
    container.querySelector('[name=detail]').value = previous.detail ?? 'balanced';
    container.querySelector('[name=glossary]').value = previous.glossary ?? '';
    container.querySelector('[name=personalize]').checked = previous.personalize ?? true;
  }
  function options(container) {
    const get = name => container.querySelector('[name=' + name + ']');
    return { detail: get('detail').value, provider: get('provider').value || 'default',
      glossary: get('glossary').value.trim(), personalize: get('personalize').checked };
  }
  function choose(previous) {
    fields(form.querySelector('.generation-host'), previous);
    dialog.returnValue = '';
    dialog.showModal();
    return new Promise(resolve => dialog.addEventListener('close', () =>
      resolve(dialog.returnValue === 'submit' ? options(form) : null), { once: true }));
  }
  return { fields, options, choose };
}
