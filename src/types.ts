export type StageId = 'ALL' | '2-1' | '2-2' | '2-3' | '2-4';

export interface ProjectSourceFile {
  path: string;
  title: string;
  stage: StageId;
  language: 'kotlin' | 'xml' | 'gradle' | 'yaml' | 'json' | 'markdown';
  summary: string;
  content: string;
}

export interface VoiceMappingEntry {
  eventId: string;
  eventTitle: string;
  triggerCondition: string;
  fileName: string;
  prefixedFileName?: string;
  originalFileName?: string;
  inferredKeywords: string[];
  enabled: boolean;
  matchedInScan?: boolean;
}
