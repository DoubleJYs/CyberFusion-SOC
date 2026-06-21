import request from './request'
import type { ApiResult, PageQuery, PageResult } from '@/types/api'
import type { BizFlowLogForm, BizFlowLogRecord, BizNoGenerateResult, BizSequenceForm, BizSequenceRecord } from '@/types/workflow'

export interface BizSequenceQuery extends PageQuery {
  enabled?: number
}

export interface BizFlowLogQuery extends PageQuery {
  bizType?: string
  bizId?: string
  bizNo?: string
  operatorName?: string
  startTime?: string
  endTime?: string
}

export async function fetchBizSequences(params: BizSequenceQuery): Promise<PageResult<BizSequenceRecord>> {
  const response = await request.get<ApiResult<PageResult<BizSequenceRecord>>>('/system/biz-sequences', { params })
  return response.data.data
}

export async function createBizSequence(data: BizSequenceForm): Promise<BizSequenceRecord> {
  const response = await request.post<ApiResult<BizSequenceRecord>>('/system/biz-sequences', data)
  return response.data.data
}

export async function updateBizSequence(id: number, data: BizSequenceForm): Promise<BizSequenceRecord> {
  const response = await request.put<ApiResult<BizSequenceRecord>>(`/system/biz-sequences/${id}`, data)
  return response.data.data
}

export async function generateBizNo(sequenceCode: string): Promise<BizNoGenerateResult> {
  const response = await request.post<ApiResult<BizNoGenerateResult>>('/system/biz-sequences/generate', { sequenceCode })
  return response.data.data
}

export async function fetchBizFlowLogs(params: BizFlowLogQuery): Promise<PageResult<BizFlowLogRecord>> {
  const response = await request.get<ApiResult<PageResult<BizFlowLogRecord>>>('/system/biz-flow-logs', { params })
  return response.data.data
}

export async function createBizFlowLog(data: BizFlowLogForm): Promise<BizFlowLogRecord> {
  const response = await request.post<ApiResult<BizFlowLogRecord>>('/system/biz-flow-logs', data)
  return response.data.data
}
