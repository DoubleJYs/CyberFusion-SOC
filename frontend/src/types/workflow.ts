export interface BizSequenceRecord {
  id: number
  sequenceCode: string
  sequenceName: string
  prefix: string
  datePattern: string
  currentValue: number
  step: number
  length: number
  resetPolicy: 'NEVER' | 'DAILY' | 'MONTHLY' | 'YEARLY' | string
  lastResetDate?: string
  enabled: number
  remark?: string
  createdAt: string
  updatedAt: string
}

export interface BizSequenceForm {
  sequenceCode?: string
  sequenceName: string
  prefix: string
  datePattern: string
  currentValue: number
  step: number
  length: number
  resetPolicy: string
  enabled: number
  remark?: string
}

export interface BizNoGenerateResult {
  sequenceCode: string
  bizNo: string
  currentValue: number
}

export interface BizFlowLogRecord {
  id: number
  bizType: string
  bizId: string
  bizNo?: string
  fromStatus?: string
  toStatus?: string
  action: string
  operatorId?: number
  operatorName?: string
  reason?: string
  remark?: string
  createdAt: string
}

export interface BizFlowLogForm {
  bizType: string
  bizId: string
  bizNo?: string
  fromStatus?: string
  toStatus?: string
  action: string
  reason?: string
  remark?: string
}
