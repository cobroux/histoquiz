export type Period = 'PREMIERE_GUERRE' | 'ENTRE_DEUX_GUERRES' | 'SECONDE_GUERRE' | 'GUERRE_FROIDE';

export type Phase = 'LOBBY' | 'QUESTION' | 'REVEAL' | 'FINISHED';

export interface PeriodInfo {
  id: Period;
  label: string;
  years: string;
  questionCount: number;
}

export interface CreateRoomRequest {
  name: string;
  periods: Period[];
  questionCount: number;
  secondsPerQuestion: number;
  solo: boolean;
}

export interface JoinResult {
  code: string;
  playerId: string;
  token: string;
}

export interface PlayerView {
  id: string;
  name: string;
  score: number;
  correctCount: number;
  streak: number;
  bestStreak: number;
  connected: boolean;
  answered: boolean;
  lastPoints: number | null;
  lastCorrect: boolean | null;
}

export interface QuestionView {
  period: Period;
  periodLabel: string;
  text: string;
  choices: string[];
}

export interface Reveal {
  correctIndex: number;
  explanation: string;
  answerCounts: number[];
}

export interface RoomView {
  code: string;
  phase: Phase;
  solo: boolean;
  hostId: string;
  settings: { periods: Period[]; questionCount: number; secondsPerQuestion: number };
  players: PlayerView[];
  questionIndex: number;
  questionCount: number;
  question: QuestionView | null;
  deadline: number;
  serverTime: number;
  reveal: Reveal | null;
}
