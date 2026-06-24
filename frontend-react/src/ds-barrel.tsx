// Design-sync entry — re-exports every component in the synced set as named exports.
// This file is only used by the design-sync build; it is never imported by the app.
export { default as TextField }          from './components/ui/TextField';
export { default as Select }             from './components/ui/Select';
export { default as TextArea }           from './components/ui/TextArea';
export { default as ConfirmDialog }      from './components/ui/ConfirmDialog';
export { default as FormModal }          from './components/ui/FormModal';
export { default as ThemeToggle }        from './components/ui/ThemeToggle';
export { default as HowToPlayPanel }     from './components/game/HowToPlayPanel';
export { default as AnimatedScorePopup } from './components/game/AnimatedScorePopup';
export { default as EntitySearch }       from './components/game/EntitySearch';
export { default as DebugPanel }         from './components/game/DebugPanel';
export { default as LobbyView }          from './components/game/lobby/LobbyView';
export { default as MatchView }          from './components/game/match/MatchView';
