import { useCallback, useMemo } from 'react'
import { useAppSelector } from '../../store/hooks'
import { selectIamState } from '../../store/iam/iamSlice'
import type { PermissionKey } from './permissions'

export const useIam = () => {
  const iam = useAppSelector(selectIamState)
  const permissionSet = useMemo(() => new Set(iam.permissions), [iam.permissions])

  const can = useCallback(
    (permission: PermissionKey) => iam.owner || permissionSet.has(permission),
    [iam.owner, permissionSet],
  )

  const canAny = useCallback(
    (permissions: readonly PermissionKey[]) =>
      iam.owner || permissions.some((permission) => permissionSet.has(permission)),
    [iam.owner, permissionSet],
  )

  const canAll = useCallback(
    (permissions: readonly PermissionKey[]) =>
      iam.owner || permissions.every((permission) => permissionSet.has(permission)),
    [iam.owner, permissionSet],
  )

  return { ...iam, can, canAny, canAll }
}
