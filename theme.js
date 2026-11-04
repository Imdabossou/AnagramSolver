// Runs before the page draws, so a chosen theme never flashes the wrong colours.
(function () {
  const KEY = 'theme';
  const ORDER = ['system', 'light', 'dark'];
  const read = () => {
    try { return localStorage.getItem(KEY) || 'system'; } catch { return 'system'; }
  };
  const apply = (choice) => {
    if (choice === 'system') document.documentElement.removeAttribute('data-theme');
    else document.documentElement.setAttribute('data-theme', choice);
  };
  apply(read());
  addEventListener('DOMContentLoaded', () => {
    const button = document.getElementById('theme');
    const label = (choice) => { button.textContent = 'Theme: ' + choice; };
    label(read());
    button.addEventListener('click', () => {
      const next = ORDER[(ORDER.indexOf(read()) + 1) % ORDER.length];
      try { localStorage.setItem(KEY, next); } catch { /* still applies for this visit */ }
      apply(next);
      label(next);
    });
  });
})();
